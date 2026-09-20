package com.example.seckill.service;

import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    /**
     * 秒杀商品列表
     * TODO 第二周优化: 加Redis缓存, 解决缓存穿透/击穿/雪崩
     */
    public List<GoodsVo> listSeckillGoods() {
        return goodsMapper.findSeckillGoodsList();
    }

    /**
     * 秒杀商品详情
     */
    public GoodsVo getSeckillGoodsDetail(Long goodsId) {
        return goodsMapper.findSeckillGoodsById(goodsId);
    }
}
