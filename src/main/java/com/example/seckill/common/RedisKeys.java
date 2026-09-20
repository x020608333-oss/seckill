package com.example.seckill.common;

/**
 * Redis Key 统一管理(面试加分项: 体现规范意识)
 * <p>
 * 命名规范: 业务:类型:id, 全部小写, 冒号分隔
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 秒杀库存: seckill:stock:{goodsId} */
    public static String seckillStock(Long goodsId) {
        return "seckill:stock:" + goodsId;
    }

    /** 售罄标记(内存标记的持久化版): seckill:empty:{goodsId} */
    public static String seckillEmpty(Long goodsId) {
        return "seckill:empty:" + goodsId;
    }

    /** 秒杀订单(防重复 + 查结果): seckill:order:{userId}:{goodsId} */
    public static String seckillOrder(Long userId, Long goodsId) {
        return "seckill:order:" + userId + ":" + goodsId;
    }

    /** 商品详情缓存: goods:detail:{goodsId} */
    public static String goodsDetail(Long goodsId) {
        return "goods:detail:" + goodsId;
    }

    /** 商品详情缓存空值(防穿透): goods:detail:null:{goodsId} */
    public static String goodsDetailNull(Long goodsId) {
        return "goods:detail:null:" + goodsId;
    }
}
