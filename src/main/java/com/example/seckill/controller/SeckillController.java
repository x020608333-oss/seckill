package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.config.UserContext;
import com.example.seckill.entity.OrderInfo;
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
     * v2: Redis预减库存(Lua原子扣减 + 内存售罄标记 + Redis防重复 + DB乐观锁兜底)
     */
    @PostMapping("/doSeckill")
    public Result<OrderInfo> doSeckill(@RequestParam Long goodsId) {
        User user = UserContext.getUser();
        OrderInfo order = seckillService.seckill(user, goodsId);
        return Result.success(order);
    }

    /**
     * 查询秒杀结果
     * v2: 优先读Redis, miss再查DB
     */
    @GetMapping("/result")
    public Result<Long> getResult(@RequestParam Long goodsId) {
        User user = UserContext.getUser();
        Long orderId = seckillService.getSeckillResult(user.getId(), goodsId);
        // orderId: 秒杀成功; -1: 没抢到; null: 排队中(v3接入MQ后有意义)
        return Result.success(orderId != null ? orderId : -1L);
    }
}
