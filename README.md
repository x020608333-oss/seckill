# 高并发秒杀系统

> 基于 Spring Boot + MySQL 的秒杀系统(持续迭代: Redis → RabbitMQ → Redisson → Docker)
> 一个为 Java 后端实习面试准备的实战项目, 完整覆盖高频面试考点

## 技术栈

Spring Boot 2.7 / MyBatis-Plus / MySQL 8 / JWT / Lombok / Hutool
(路线图: Redis + Lua / RabbitMQ / Redisson / Docker / JMeter)

## 版本迭代路线

| 版本 | 内容 | 状态 |
|------|------|------|
| v1 | 纯数据库秒杀: 乐观锁防超卖 + 唯一索引防重复 + 事务 | ✅ 已完成 |
| v2 | Redis: 库存预热 + Lua原子扣减 + 内存售罄标记 + 商品缓存三连防护 | ✅ 已完成 |
| v3 | RabbitMQ异步下单削峰 + 手动ACK + 失败补偿 + 结果轮询 | ✅ 已完成 |
| v4 | Redisson分布式锁 + RRateLimiter接口限流 + 压测工具 + Docker Compose | ✅ 已完成 |

## 压测报告(v4, 单机实测)

压测工具: 内置 JUnit 压测类 `LoadTest` (200 线程并发, CountDownLatch 同步起跑), 等价于 JMeter 的轻量实现

| 指标 | 结果 |
|------|------|
| 并发线程 | 200 (同时起跑) |
| 总耗时 | 975 ms |
| 端到端 QPS | ~205 |
| 成功入队 | 100 |
| 售罄失败 | 100 |
| 其他异常 | 0 |
| DB 剩余库存 | 0 (**无超卖**) |
| DB 订单数 | 100 (**与库存扣减完全一致**) |

运行压测:

```bash
# 1. 启动项目(先启动 MySQL / Redis / RabbitMQ)
# 2. 执行
.\mvnw.cmd test -Dtest=LoadTest
```

## 一键部署(Docker Compose)

```bash
docker compose up -d
# 访问 http://localhost:8080/
# RabbitMQ 管理台 http://localhost:15672 (guest/guest)
```

包含服务: MySQL 8(自动初始化建表) + Redis 7(AOF持久化) + RabbitMQ 3.13 + 应用,
应用等中间件健康检查通过后才会启动。

## 秒杀核心链路(v4 完整版)

```
用户请求
   ↓
[拦截器] JWT鉴权
   ↓
[Redisson限流] 单用户每秒最多5次(防脚本刷单)
   ↓
[Redisson分布式锁] 同一用户并发请求串行化(防重复提交)
   ↓
[内存] 售罄标记      → 已售罄? 直接失败(0 IO)
   ↓
[Redis] 订单Key判重  → 重复秒杀? 直接失败
   ↓
[Redis] Lua原子扣减库存 → 库存不足? 打售罄标记+失败
   ↓
[Redis] 写订单占位Key("1"=排队中)
   ↓
[RabbitMQ] 发消息 → 接口立即返回"排队中"(毫秒级)
   ↓ ................................. 异步边界
[MQ消费者] 取消息(prefetch=1, 手动ACK)
   ↓
[MySQL] 乐观锁扣库存 + 建单(事务, 唯一索引兜底)
   ↓
[Redis] 订单Key回写真实订单ID
   ↓
用户轮询 /seckill/result → 拿到订单ID
```

### 为什么这样设计(面试话术)

- **削峰填谷**: 接口只做内存和Redis操作，1万QPS打进来也不会压垮数据库；MQ按自身消费能力拉取
- **最终一致**: 建单失败时回滚Redis库存(`compensateRedis`)，保证Redis与DB库存一致
- **幂等**: DB `(user_id, goods_id)` 唯一索引兜底，消息重复投递也不会重复建单
- **手动ACK**: 业务成功才ack，系统异常nack重回队列，避免消息丢失
- **分布式锁**: Redisson 保证同用户并发请求串行执行，watchdog自动续期防锁提前释放
- **限流**: Redisson RRateLimiter 基于令牌桶，天然支持分布式限流(对比Guava只能单机)

### 缓存三连问防护(GoodsService)

- **缓存穿透** → 缓存空值(短TTL 60秒), 防恶意刷不存在的商品ID
- **缓存击穿** → 秒杀商品详情几乎不变, 采用长TTL + 手动失效(管理端变更时调 `evictGoodsCache`)
- **缓存雪崩** → 随机TTL(1小时 + 0~10分钟), 避免大量key同一时刻过期

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+(项目自带 Maven Wrapper, 无需手动安装)
- MySQL 8.x

### 1. 初始化数据库

```bash
mysql -uroot -p < src/main/resources/sql/schema.sql
```

### 2. 配置数据库密码(可选)

默认使用环境变量, 不配置则使用默认值:

```bash
# Windows
set DB_USERNAME=root
set DB_PASSWORD=你的密码

# Linux/macOS
export DB_USERNAME=root
export DB_PASSWORD=你的密码
```

### 3. 启动项目

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux/macOS
./mvnw spring-boot:run
```

### 3.1 依赖中间件(按版本需要)

| 版本 | 需要组件 | Windows 本地安装 |
|------|----------|------------------|
| v1 | MySQL | 常规安装即可 |
| v2 | + Redis | 下载 msi/zip 或 `docker run -d -p 6379:6379 redis` |
| v3 | + RabbitMQ | 需先装 Erlang, 再装 RabbitMQ 并启动服务 |

> 未安装对应中间件时, 可在 `SeckillApplication` 的 `exclude` 中排除自动装配后先跑 v1。

### 4. 网页测试控制台

浏览器打开 http://localhost:8080/ , 默认测试账号 `admin / 123456`,
点"登录" → 点"立即秒杀"即可完整体验。

### 5. 接口测试(curl)

```bash
# 登录获取token (测试账号 admin / 123456)
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"

# 查看秒杀商品列表
curl http://localhost:8080/goods/list -H "Authorization: Bearer <token>"

# 发起秒杀
curl -X POST "http://localhost:8080/seckill/doSeckill?goodsId=1" \
  -H "Authorization: Bearer <token>"

# 查询秒杀结果
curl "http://localhost:8080/seckill/result?goodsId=1" -H "Authorization: Bearer <token>"
```

## 核心设计

### 防超卖(v1)

```sql
UPDATE seckill_goods SET stock_count = stock_count - 1
WHERE goods_id = ? AND stock_count > 0
```

- UPDATE 持有行锁, `stock_count > 0` 由数据库原子判断, 从根上杜绝负数库存
- 影响行数 = 0 → 库存不足, 直接失败

### 防重复秒杀(v1)

- `seckill_order` 表 `(user_id, goods_id)` 唯一索引
- 重复下单触发 `DuplicateKeyException`, 事务整体回滚, 库存自动恢复
- 这是防超卖的最后一道防线: 即使上层所有判断都失效, 数据库也能兜住

### 认证方案

- JWT 无状态登录, 拦截器统一鉴权
- ThreadLocal 保存当前用户上下文, 请求结束自动清理(防线程复用污染)

## 项目结构

```
src/main/java/com/example/seckill
├── SeckillApplication       启动类
├── common/                  统一返回体 + 全局异常处理
├── config/                  JWT拦截器 + ThreadLocal用户上下文 + 文档配置
├── controller/              接口层(用户/商品/秒杀)
├── service/                 业务层(秒杀核心逻辑)
├── mapper/                  MyBatis-Plus数据层
├── entity/                  数据库实体
├── vo/                      视图对象
└── util/                    JWT工具类
src/main/resources
├── application.yml          配置文件(密码通过环境变量注入)
├── sql/schema.sql           建表脚本 + 测试数据
└── static/index.html        网页测试控制台
```

