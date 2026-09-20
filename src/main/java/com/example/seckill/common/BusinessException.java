package com.example.seckill.common;

import lombok.Getter;

/**
 * 业务异常: 秒杀失败、库存不足等业务场景抛出, 由全局异常处理器统一处理
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
