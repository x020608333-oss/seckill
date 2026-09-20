package com.example.seckill.vo;

import com.example.seckill.entity.Goods;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情 + 秒杀信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsVo extends Goods {

    private Long seckillGoodsId;

    private BigDecimal seckillPrice;

    private Integer stockCount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}
