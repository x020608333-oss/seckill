package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.Goods;
import com.example.seckill.vo.GoodsVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GoodsMapper extends BaseMapper<Goods> {

    @Select("SELECT g.*, sg.id AS seckillGoodsId, sg.seckill_price, sg.stock_count, " +
            "sg.start_date, sg.end_date " +
            "FROM goods g JOIN seckill_goods sg ON g.id = sg.goods_id")
    List<GoodsVo> findSeckillGoodsList();

    @Select("SELECT g.*, sg.id AS seckillGoodsId, sg.seckill_price, sg.stock_count, " +
            "sg.start_date, sg.end_date " +
            "FROM goods g JOIN seckill_goods sg ON g.id = sg.goods_id " +
            "WHERE g.id = #{goodsId}")
    GoodsVo findSeckillGoodsById(@Param("goodsId") Long goodsId);
}
