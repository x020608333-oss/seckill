-- ============================================================
-- 秒杀系统数据库初始化脚本
-- 在 MySQL 中执行: mysql -uroot -p < schema.sql
-- 或登录 MySQL 后: source <项目路径>/src/main/resources/sql/schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE seckill;

-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(32)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(128) NOT NULL COMMENT '密码(MD5加盐)',
    salt        VARCHAR(16)  NOT NULL COMMENT '盐',
    nickname    VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) ENGINE = InnoDB COMMENT '用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS goods (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    goods_name   VARCHAR(100)  NOT NULL COMMENT '商品名称',
    goods_title  VARCHAR(200)  DEFAULT NULL COMMENT '商品标题',
    goods_img    VARCHAR(255)  DEFAULT NULL COMMENT '商品图片',
    goods_detail TEXT          COMMENT '商品详情',
    goods_price  DECIMAL(10,2) NOT NULL COMMENT '原价',
    goods_stock  INT           NOT NULL DEFAULT 0 COMMENT '普通库存',
    create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB COMMENT '商品表';

-- 秒杀商品表
CREATE TABLE IF NOT EXISTS seckill_goods (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    goods_id       BIGINT       NOT NULL COMMENT '商品ID',
    seckill_price  DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock_count    INT          NOT NULL COMMENT '秒杀库存',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    start_date     DATETIME     NOT NULL COMMENT '秒杀开始时间',
    end_date       DATETIME     NOT NULL COMMENT '秒杀结束时间',
    UNIQUE KEY uk_goods_id (goods_id)
) ENGINE = InnoDB COMMENT '秒杀商品表';

-- 订单表
CREATE TABLE IF NOT EXISTS order_info (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    goods_id     BIGINT       NOT NULL COMMENT '商品ID',
    goods_name   VARCHAR(100) NOT NULL COMMENT '商品名称(冗余)',
    goods_count  INT          NOT NULL DEFAULT 1,
    goods_price  DECIMAL(10,2) NOT NULL COMMENT '成交价格',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0新建未支付 1已支付 2已取消',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    pay_date     DATETIME     DEFAULT NULL COMMENT '支付时间',
    KEY idx_user_id (user_id)
) ENGINE = InnoDB COMMENT '订单表';

-- 秒杀订单表(防重复秒杀的唯一索引是防超卖最后一道防线)
CREATE TABLE IF NOT EXISTS seckill_order (
    id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id  BIGINT NOT NULL COMMENT '用户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    goods_id BIGINT NOT NULL COMMENT '商品ID',
    UNIQUE KEY uk_user_goods (user_id, goods_id) COMMENT '同一用户同一商品只能秒杀一次'
) ENGINE = InnoDB COMMENT '秒杀订单表';

-- 测试数据: 两个商品 + 对应秒杀活动
INSERT INTO goods (id, goods_name, goods_title, goods_price, goods_stock, goods_detail) VALUES
(1, 'iPhone 15 Pro', 'Apple iPhone 15 Pro 256G 原色钛金属', 8999.00, 1000, 'A17 Pro芯片'),
(2, '小米14', '小米14 16G+512G 黑色', 4299.00, 1000, '骁龙8 Gen3');

INSERT INTO seckill_goods (goods_id, seckill_price, stock_count, start_date, end_date) VALUES
(1, 6999.00, 100, '2024-01-01 00:00:00', '2035-12-31 23:59:59'),
(2, 3299.00, 200, '2024-01-01 00:00:00', '2035-12-31 23:59:59');

-- 测试用户: 用户名 admin / 密码 123456 (MD5加盐存储)
INSERT INTO user (username, password, salt, nickname) VALUES
('admin', MD5(CONCAT('1qaz2wsx', '123456')), '1qaz2wsx', '管理员');
