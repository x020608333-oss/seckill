package com.example.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.entity.OrderInfo;
import com.example.seckill.entity.SeckillGoods;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.OrderInfoMapper;
import com.example.seckill.mapper.SeckillGoodsMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 秒杀核心服务
 * <p>
 * 第一版(纯数据库): 乐观锁防超卖 + 唯一索引防重复下单 + 事务保证原子性
 * 第二版(第二周):  + Redis预减库存 + Lua原子扣减
 * 第三版(第三周):  + RabbitMQ异步下单削峰 + Redisson限流
 */
@Slf4j
@Service
public class SeckillService {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private GoodsService goodsService;

    /**
     * 秒杀下单(第一版: 纯数据库方案)
     *
     * 防超卖原理:
     * 1. UPDATE ... SET stock = stock - 1 WHERE stock > 0
     *    UPDATE语句本身持有行锁, stock > 0 条件由数据库原子判断, 不会扣成负数
     * 2. seckill_order 表 (user_id, goods_id) 唯一索引
     *    同一用户重复秒杀同一商品时插入抛 DuplicateKeyException
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderInfo seckill(User user, Long goodsId) {
        // 1. 查询秒杀商品, 校验活动时间
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

        // 2. 数据库乐观锁扣减库存(影响行数0说明库存不足)
        int rows = seckillGoodsMapper.reduceStock(goodsId);
        if (rows == 0) {
            throw new BusinessException("手慢了, 商品已抢完");
        }

        // 3. 创建订单(插入秒杀订单表, 唯一索引防重复秒杀)
        try {
            return createOrder(user, goodsVo);
        } catch (DuplicateKeyException e) {
            // 触发(user_id, goods_id)唯一索引: 重复秒杀
            // 注意: 事务会整体回滚, 库存也会恢复
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
     */
    public SeckillOrder getSeckillResult(Long userId, Long goodsId) {
        return seckillOrderMapper.selectOne(new QueryWrapper<SeckillOrder>()
                .eq("user_id", userId)
                .eq("goods_id", goodsId));
    }
}
