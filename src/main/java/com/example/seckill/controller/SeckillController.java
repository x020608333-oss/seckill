package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.config.UserContext;
import com.example.seckill.entity.OrderInfo;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.entity.User;
import com.example.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀接口
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 秒杀下单
     * 第一版: 数据库乐观锁防超卖
     */
    @PostMapping("/doSeckill")
    public Result<OrderInfo> doSeckill(@RequestParam Long goodsId) {
        User user = UserContext.getUser();
        OrderInfo order = seckillService.seckill(user, goodsId);
        return Result.success(order);
    }

    /**
     * 查询秒杀结果
     */
    @GetMapping("/result")
    public Result<Long> getResult(@RequestParam Long goodsId) {
        User user = UserContext.getUser();
        SeckillOrder order = seckillService.getSeckillResult(user.getId(), goodsId);
        // orderId: 秒杀成功; -1: 没抢到; null: 排队中(第三版接入MQ后有意义)
        Long result = order != null ? order.getOrderId() : -1L;
        return Result.success(result);
    }
}
