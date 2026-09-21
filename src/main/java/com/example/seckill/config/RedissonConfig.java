package com.example.seckill.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置(v4)
 * <p>
 * 用途:
 * 1. 分布式锁(RLock): 同一用户并发重复请求串行化, 防止"一人多单"
 * 2. 限流器(RRateLimiter): 基于令牌桶, 限制单用户接口调用频率, 防脚本刷单
 * <p>
 * 面试话术: 为什么用 Redisson 而不是自己写 setnx?
 * - 自带看门狗(watchdog)自动续期, 业务没执行完锁不会提前过期
 * - 可重入, 支持 tryLock 等待机制, 避免"锁过期 + 业务未完成"的经典坑
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private String port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(32);
        return Redisson.create(config);
    }
}
