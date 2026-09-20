package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

    /**
     * 数据库乐观锁扣减库存(防超卖兜底方案)
     * UPDATE 自带行锁, stock_count > 0 条件保证不会扣成负数
     * @return 影响行数: 1扣减成功 0库存不足
     */
    @Update("UPDATE seckill_goods SET stock_count = stock_count - 1 " +
            "WHERE goods_id = #{goodsId} AND stock_count > 0")
    int reduceStock(@Param("goodsId") Long goodsId);
}
