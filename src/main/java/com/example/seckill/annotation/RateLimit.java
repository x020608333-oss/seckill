package com.example.seckill.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解(v4)
 * <p>
 * 基于 Redisson RRateLimiter(令牌桶)实现, 粒度: 单用户
 * 用法: @RateLimit(count = 5, period = 1)  表示该用户每秒最多调5次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的请求数 */
    long count() default 5;

    /** 时间窗口(秒) */
    long period() default 1;

    /** 限流提示语 */
    String message() default "请求过于频繁, 请稍后再试";
}
