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
| v2 | Redis: 库存预热 + Lua原子扣减 + 商品缓存(穿透/击穿/雪崩防护) | ⬜ 进行中 |
| v3 | RabbitMQ异步下单削峰 + 延迟队列关单 + Redisson分布式锁限流 | ⬜ 计划中 |
| v4 | JMeter压测调优 + Docker Compose部署 + 压测报告 | ⬜ 计划中 |

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

