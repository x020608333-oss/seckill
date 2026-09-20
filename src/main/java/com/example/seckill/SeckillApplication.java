package com.example.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;

// 第一版(纯数据库)暂未安装Redis/RabbitMQ, 先排除自动装配
// 第二周安装Redis后, 删除exclude即可
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RabbitAutoConfiguration.class, RedissonAutoConfiguration.class})
@MapperScan("com.example.seckill.mapper")
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}
