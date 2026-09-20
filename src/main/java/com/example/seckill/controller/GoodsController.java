package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.service.GoodsService;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口
 */
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    /**
     * 秒杀商品列表
     */
    @GetMapping("/list")
    public Result<List<GoodsVo>> list() {
        return Result.success(goodsService.listSeckillGoods());
    }

    /**
     * 秒杀商品详情
     */
    @GetMapping("/detail/{goodsId}")
    public Result<GoodsVo> detail(@PathVariable Long goodsId) {
        return Result.success(goodsService.getSeckillGoodsDetail(goodsId));
    }
}
