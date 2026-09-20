package com.example.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

// v2: Redis已启用; RabbitMQ(v3)/Redisson(v3)暂未安装, 继续排除自动装配
@SpringBootApplication(exclude = {RabbitAutoConfiguration.class, RedissonAutoConfiguration.class})
@MapperScan("com.example.seckill.mapper")
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}
