package com.example.seckill.service;

import com.example.seckill.common.BusinessException;
import com.example.seckill.entity.OrderInfo;
import com.example.seckill.entity.User;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.SeckillMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * MQ 消费者: 异步落库(v3)
 * <p>
 * 面试话术:
 * 1. 削峰填谷: 接口瞬间承受1万QPS, MQ按prefetch=1慢慢消费, DB压力平稳
 * 2. 手动ACK: 建单成功才确认, 失败nack进死信/重试
 * 3. 幂等设计: DB唯一索引兜底, 重复消费不会重复建单
 * 4. 补偿机制: 建单失败回滚Redis库存, 保证最终一致
 */
@Slf4j
@Service
public class MqConsumer {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private GoodsService goodsService;

    @RabbitListener(queues = "seckill.order.queue")
    public void onSeckillMessage(SeckillMessage message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long userId = message.getUserId();
        Long goodsId = message.getGoodsId();
        log.info("MQ消费: userId={}, goodsId={}", userId, goodsId);

        try {
            // 查商品(走缓存, 消费者不赶时间)
            GoodsVo goodsVo = goodsService.getSeckillGoodsDetail(goodsId);
            if (goodsVo == null) {
                log.warn("商品不存在, 丢弃消息: goodsId={}", goodsId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 构造User对象(只需id, 避免一次用户查询)
            User user = new User();
            user.setId(userId);

            // 落库: DB乐观锁扣减 + 建单(事务)
            OrderInfo order = seckillService.createOrderWithDbStock(user, goodsVo);

            // 成功: 回写订单ID到Redis, 用户轮询 /result 能拿到
            seckillService.markOrderCreated(userId, goodsId, order.getId());
            channel.basicAck(deliveryTag, false);
            log.info("MQ消费成功: orderId={}", order.getId());
        } catch (BusinessException e) {
            // 业务失败(重复秒杀/库存不足): 回滚Redis, ack掉不重试
            seckillService.compensateRedis(userId, goodsId);
            channel.basicAck(deliveryTag, false);
            log.warn("MQ消费业务失败: {}", e.getMessage());
        } catch (Exception e) {
            // 系统异常: 回滚Redis + nack重回队列(最多重试3次, 面试话术: 可配死信队列)
            seckillService.compensateRedis(userId, goodsId);
            channel.basicNack(deliveryTag, false, true);
            log.error("MQ消费异常, 消息重回队列", e);
        }
    }
}
