package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.RedisKeys;
import com.example.seckill.entity.OrderInfo;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.OrderInfoMapper;
import com.example.seckill.mapper.SeckillGoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 秒杀核心服务
 * <p>
 * v1(纯数据库): 乐观锁防超卖 + 唯一索引防重复下单 + 事务保证原子性
 * v2(Redis预减库存): Lua原子扣减 + 内存售罄标记 + Redis防重复 + DB乐观锁兜底
 * v3(计划): + RabbitMQ异步下单削峰 + Redisson限流
 */
@Slf4j
@Service
public class SeckillService implements InitializingBean {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DefaultRedisScript<Long> stockDeductScript;

    /**
     * 内存售罄标记: 库存打完后不再访问Redis, 直接快速失败
     * key: goodsId, value: true=已售罄
     */
    private final Map<Long, Boolean> emptyStockMap = new ConcurrentHashMap<>();

    /**
     * 项目启动时把所有秒杀商品库存预热到Redis(InitializingBean回调)
     * 面试话术: "库存预热, 避免秒杀开始瞬间大量请求打到数据库"
     */
    @Override
    public void afterPropertiesSet() {
        List<GoodsVo> goodsList = goodsService.listSeckillGoods();
        if (goodsList == null) {
            return;
        }
        for (GoodsVo goods : goodsList) {
            redisTemplate.opsForValue().set(
                    RedisKeys.seckillStock(goods.getId()),
                    goods.getStockCount());
            redisTemplate.delete(RedisKeys.seckillEmpty(goods.getId()));
            emptyStockMap.put(goods.getId(), false);
            log.info("库存预热: goodsId={}, stock={}", goods.getId(), goods.getStockCount());
        }
    }

    /**
     * 秒杀下单(v2: Redis预减库存方案)
     *
     * 五道关卡(按成本从低到高排列, 层层过滤):
     * 1. 内存售罄标记(无IO, 纳秒级) -> 已售罄直接失败
     * 2. Redis订单Key判重(一次内存查询) -> 重复秒杀直接失败
     * 3. Lua脚本原子扣减Redis库存(一次网络RTT) -> 库存不足则标记售罄
     * 4. 数据库乐观锁扣减(兜底, 保证与DB最终一致)
     *    注意: 此时并发已极低(只有抢到Redis库存的请求能进来)
     * 5. 唯一索引防重复(最后一道防线) + Redis订单Key回滚补偿
     */
    public OrderInfo seckill(User user, Long goodsId) {
        // 关卡0: 校验活动时间(读商品详情走缓存, 见 GoodsService)
        GoodsVo goodsVo = goodsService.getSeckillGoodsDetail(goodsId);
        if (goodsVo == null) {
            throw new BusinessException("秒杀商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goodsVo.getStartDate())) {
            throw new BusinessException("秒杀还未开始");
        }
        if (now.isAfter(goodsVo.getEndDate())) {
            throw new BusinessException("秒杀已结束");
        }

        // 关卡1: 内存售罄标记
        if (Boolean.TRUE.equals(emptyStockMap.get(goodsId))) {
            throw new BusinessException("手慢了, 商品已抢完");
        }

        String orderKey = RedisKeys.seckillOrder(user.getId(), goodsId);

        // 关卡2: Redis判重(同一用户同一商品只能抢一次)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(orderKey))) {
            throw new BusinessException("每人限购一件, 请勿重复秒杀");
        }

        // 关卡3: Lua原子扣减Redis库存
        Long deductResult = redisTemplate.execute(
                stockDeductScript,
                Collections.singletonList(RedisKeys.seckillStock(goodsId)),
                "1");
        if (deductResult == null || deductResult == 0) {
            // 库存不足: 打上内存 + Redis 双标记, 后续请求快速失败
            emptyStockMap.put(goodsId, true);
            redisTemplate.opsForValue().set(RedisKeys.seckillEmpty(goodsId), "1");
            throw new BusinessException("手慢了, 商品已抢完");
        }

        // 关卡4+5: 落库(事务内: DB乐观锁扣减 + 建单, 唯一索引兜底)
        // 注意: 先占Redis订单Key, 防止并发重复; 失败时删除Key做补偿
        redisTemplate.opsForValue().set(orderKey, "1");
        try {
            OrderInfo order = createOrderWithDbStock(user, goodsVo);
            // 落库成功: 把订单ID回写Redis, 供 /result 查询
            redisTemplate.opsForValue().set(orderKey, String.valueOf(order.getId()));
            return order;
        } catch (BusinessException e) {
            // 重复秒杀/库存不足: 把Redis库存加回去 + 删除订单占位Key
            redisTemplate.opsForValue().increment(RedisKeys.seckillStock(goodsId), 1);
            redisTemplate.delete(orderKey);
            // 如果是DB库存不足导致的, 同步打上售罄标记
            if ("手慢了, 商品已抢完".equals(e.getMessage())) {
                emptyStockMap.put(goodsId, true);
            }
            throw e;
        }
    }

    /**
     * 落库: 数据库乐观锁扣减库存 + 创建订单(事务保证原子性)
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderInfo createOrderWithDbStock(User user, GoodsVo goodsVo) {
        int rows = seckillGoodsMapper.reduceStock(goodsVo.getId());
        if (rows == 0) {
            throw new BusinessException("手慢了, 商品已抢完");
        }
        try {
            return createOrder(user, goodsVo);
        } catch (DuplicateKeyException e) {
            // 触发(user_id, goods_id)唯一索引: 重复秒杀, 事务整体回滚库存恢复
            throw new BusinessException("每人限购一件, 请勿重复秒杀");
        }
    }

    private OrderInfo createOrder(User user, GoodsVo goodsVo) {
        OrderInfo order = new OrderInfo();
        order.setUserId(user.getId());
        order.setGoodsId(goodsVo.getId());
        order.setGoodsName(goodsVo.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(goodsVo.getSeckillPrice());
        order.setStatus(OrderInfo.STATUS_NEW);
        orderInfoMapper.insert(order);

        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(user.getId());
        seckillOrder.setOrderId(order.getId());
        seckillOrder.setGoodsId(goodsVo.getId());
        seckillOrderMapper.insert(seckillOrder);

        log.info("秒杀成功: userId={}, goodsId={}, orderId={}", user.getId(), goodsVo.getId(), order.getId());
        return order;
    }

    /**
     * 查询用户的秒杀结果
     * v2: 优先读Redis(订单ID回写),  miss 再查DB
     *
     * @return 订单ID; null=没抢到
     */
    public Long getSeckillResult(Long userId, Long goodsId) {
        Object orderId = redisTemplate.opsForValue().get(RedisKeys.seckillOrder(userId, goodsId));
        if (orderId != null) {
            String s = String.valueOf(orderId);
            // "1"是占位符(正在落库中), v3接入MQ后这里返回"排队中"
            if (!"1".equals(s)) {
                return Long.valueOf(s);
            }
        }
        SeckillOrder order = seckillOrderMapper.selectOne(new QueryWrapper<SeckillOrder>()
                .eq("user_id", userId)
                .eq("goods_id", goodsId));
        return order != null ? order.getOrderId() : null;
    }
}
