package com.example.seckill.aspect;

import com.example.seckill.annotation.RateLimit;
import com.example.seckill.common.BusinessException;
import com.example.seckill.config.UserContext;
import com.example.seckill.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 限流切面(v4)
 * <p>
 * 原理: Redisson RRateLimiter 基于 Redis 令牌桶, 天然支持分布式限流
 * (对比 Guava RateLimiter 只能单机限流, 多实例部署时每台机器各放一份令牌, 限流失效)
 * <p>
 * key 设计: rate:{方法名}:{用户ID}  -> 单用户维度
 * 面试话术: 生产环境可再叠加 Nginx 层IP限流 + 网关层全局限流, 形成多级防护
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:";

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        User user = UserContext.getUser();
        // 未登录用户用IP兜底(本项目接口都在拦截器后, 一般不会走到这)
        String identity = user != null ? String.valueOf(user.getId()) : "anonymous";
        String key = KEY_PREFIX + method.getName() + ":" + identity;

        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // 幂等设置: 每个时间窗口 rateLimit.period() 秒内最多 rateLimit.count() 次
        limiter.trySetRate(RateType.OVERALL, rateLimit.count(), rateLimit.period(), RateIntervalUnit.SECONDS);

        if (!limiter.tryAcquire(1)) {
            log.warn("触发限流: key={}", key);
            throw new BusinessException(429, rateLimit.message());
        }
        return joinPoint.proceed();
    }
}
