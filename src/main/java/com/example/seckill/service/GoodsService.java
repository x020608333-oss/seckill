package com.example.seckill.service;

import com.example.seckill.common.RedisKeys;
import com.example.seckill.mapper.GoodsMapper;
import com.example.seckill.vo.GoodsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务(v2: Redis缓存)
 * <p>
 * 缓存三连问防护:
 * 1. 穿透 -> 缓存空值(不存在的商品ID缓存短TTL空标记)
 * 2. 击穿 -> 热点key永不过期(秒杀场景库存由专门的stock key管理, 详情数据几乎不变)
 * 3. 雪崩 -> 随机TTL过期, 避免同一时刻大量key失效
 */
@Slf4j
@Service
public class GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 秒杀商品列表(列表数据量小, 直接查库; 详情页才走缓存)
     */
    public List<GoodsVo> listSeckillGoods() {
        return goodsMapper.findSeckillGoodsList();
    }

    /**
     * 秒杀商品详情(读多写少, 缓存加速)
     */
    public GoodsVo getSeckillGoodsDetail(Long goodsId) {
        String key = RedisKeys.goodsDetail(goodsId);
        String nullKey = RedisKeys.goodsDetailNull(goodsId);

        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof GoodsVo) {
            return (GoodsVo) cached;
        }

        // 2. 防穿透: 空值标记存在, 直接返回null
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            return null;
        }

        // 3. 回源数据库
        GoodsVo goodsVo = goodsMapper.findSeckillGoodsById(goodsId);
        if (goodsVo == null) {
            // 防穿透: 缓存空值, 短TTL(60秒, 防止误伤后上架的商品)
            redisTemplate.opsForValue().set(nullKey, "1", 60, TimeUnit.SECONDS);
            return null;
        }

        // 4. 写缓存: 随机TTL(防雪崩), 基础1小时 + 0~10分钟随机
        long ttlSeconds = 3600 + (long) (Math.random() * 600);
        redisTemplate.opsForValue().set(key, goodsVo, ttlSeconds, TimeUnit.SECONDS);
        return goodsVo;
    }

    /**
     * 商品信息变更时清缓存(管理端改价/改库存时调用, 本项目暂无管理端, 留作面试话术)
     */
    public void evictGoodsCache(Long goodsId) {
        redisTemplate.delete(RedisKeys.goodsDetail(goodsId));
        redisTemplate.delete(RedisKeys.goodsDetailNull(goodsId));
    }
}
