package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.common.RedisKeys;
import com.example.seckill.config.MqConfig;
import com.example.seckill.entity.OrderInfo;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.OrderInfoMapper;
import com.example.seckill.mapper.SeckillGoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.SeckillMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀核心服务
 * <p>
 * v1(纯数据库): 乐观锁防超卖 + 唯一索引防重复下单 + 事务保证原子性
 * v2(Redis预减库存): Lua原子扣减 + 内存售罄标记 + Redis防重复 + DB乐观锁兜底
 * v3(MQ异步下单): 接口只做预检+预减+发消息立即返回"排队中", 消费者异步建单
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

    /**
     * 库存/订单Key专用: 纯字符串操作
     * (Lua脚本要求纯数字字符串, 对象序列化器会把"1"变成"\"1\""导致tonumber失败)
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * v4: 分布式锁, 保证"同一用户+同一商品"的并发请求串行执行
     */
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private DefaultRedisScript<Long> stockDeductScript;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.seckillStock(goods.getId()),
                    String.valueOf(goods.getStockCount()));
            stringRedisTemplate.delete(RedisKeys.seckillEmpty(goods.getId()));
            emptyStockMap.put(goods.getId(), false);
            log.info("库存预热: goodsId={}, stock={}", goods.getId(), goods.getStockCount());
        }
    }

    /**
     * 秒杀下单(v3: MQ异步方案)
     *
     * 接口侧(快路径, 只做内存操作, 毫秒级返回"排队中"):
     * 1. 内存售罄标记 -> 快速失败
     * 2. Redis订单Key判重 -> 重复秒杀失败
     * 3. Lua原子扣减Redis库存 -> 库存不足失败
     * 4. 发MQ消息 + 占位订单Key -> 返回void(Controller层返回"排队中")
     *
     * 消费者侧(慢路径, 异步落库): 见 MqConsumer
     */
    public void seckill(User user, Long goodsId) {
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

        if (Boolean.TRUE.equals(emptyStockMap.get(goodsId))) {
            throw new BusinessException("手慢了, 商品已抢完");
        }

        // v4: 分布式锁 — 同一用户对同一商品的并发重复请求串行化
        // tryLock(0, 3s): 不等待(等待0ms), 拿不到锁说明有并发请求正在处理, 直接拒绝
        // 锁内只做"判重 + 扣库存 + 发消息", 耗时短; 看门狗(watchdog)自动续期防提前释放
        RLock lock = redissonClient.getLock("lock:seckill:" + user.getId() + ":" + goodsId);
        boolean locked;
        try {
            locked = lock.tryLock(0, 3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙, 请稍后重试");
        }
        if (!locked) {
            throw new BusinessException("您有请求正在处理中, 请勿重复提交");
        }

        try {
            String orderKey = RedisKeys.seckillOrder(user.getId(), goodsId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(orderKey))) {
                throw new BusinessException("每人限购一件, 请勿重复秒杀");
            }

            Long deductResult = stringRedisTemplate.execute(
                    stockDeductScript,
                    Collections.singletonList(RedisKeys.seckillStock(goodsId)),
                    "1");
            if (deductResult == null || deductResult == 0) {
                emptyStockMap.put(goodsId, true);
                stringRedisTemplate.opsForValue().set(RedisKeys.seckillEmpty(goodsId), "1");
                throw new BusinessException("手慢了, 商品已抢完");
            }

            // 占位订单Key("1"=排队中), 防并发重复
            stringRedisTemplate.opsForValue().set(orderKey, "1");
            try {
                rabbitTemplate.convertAndSend(
                        MqConfig.SECKILL_EXCHANGE,
                        MqConfig.SECKILL_ROUTING_KEY,
                        new SeckillMessage(user.getId(), goodsId));
                log.info("秒杀请求已入队: userId={}, goodsId={}", user.getId(), goodsId);
            } catch (Exception e) {
                // 发消息失败: 回滚Redis库存和占位Key
                stringRedisTemplate.opsForValue().increment(RedisKeys.seckillStock(goodsId), 1);
                stringRedisTemplate.delete(orderKey);
                log.error("发送MQ消息失败", e);
                throw new BusinessException("系统繁忙, 请稍后重试");
            }
        } finally {
            // 只释放当前线程持有的锁(避免释放别人的锁)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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

    /**
     * 消费者建单失败时的Redis补偿: 库存+1, 删除订单占位Key
     */
    public void compensateRedis(Long userId, Long goodsId) {
        stringRedisTemplate.opsForValue().increment(RedisKeys.seckillStock(goodsId), 1);
        stringRedisTemplate.delete(RedisKeys.seckillOrder(userId, goodsId));
        emptyStockMap.put(goodsId, false);
        stringRedisTemplate.delete(RedisKeys.seckillEmpty(goodsId));
        log.info("Redis补偿完成: userId={}, goodsId={}", userId, goodsId);
    }

    /**
     * 落库成功后的Redis回写: 占位Key"1" -> 真实订单ID
     */
    public void markOrderCreated(Long userId, Long goodsId, Long orderId) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.seckillOrder(userId, goodsId),
                String.valueOf(orderId));
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
     * v3: 先读Redis
     *
     * @return 订单ID(成功) / 0(排队中) / null(没抢到)
     */
    public Long getSeckillResult(Long userId, Long goodsId) {
        String oid = stringRedisTemplate.opsForValue().get(RedisKeys.seckillOrder(userId, goodsId));
        if (oid != null) {
            if ("1".equals(oid)) {
                // 占位符: MQ消费者还在建单
                return 0L;
            }
            return Long.valueOf(oid);
        }
        SeckillOrder order = seckillOrderMapper.selectOne(new QueryWrapper<SeckillOrder>()
                .eq("user_id", userId)
                .eq("goods_id", goodsId));
        return order != null ? order.getOrderId() : null;
    }
}
