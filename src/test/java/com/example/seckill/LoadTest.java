package com.example.seckill;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀接口压测(v4)
 * <p>
 * 与 JMeter 等价的轻量方案: 200 线程并发打接口, 输出端到端 QPS 与成功率
 * 运行方式: mvnw test -Dtest=LoadTest
 * <p>
 * 压测思路(面试话术):
 * 1. 预置 N 个用户, 每人只发 1 次请求, 避免"同一用户限购"干扰吞吐统计
 * 2. CountDownLatch 让所有线程同一时刻发起, 模拟真实秒杀瞬时峰值
 * 3. 分别统计成功入队 / 售罄失败 / 被限流, 并核对DB最终一致性
 */
public class LoadTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/seckill"
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER =
            System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "root";
    private static final String DB_PASS =
            System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "1qazmlp0";

    /** 并发用户数 */
    private static final int USERS = 200;
    /** 压测商品ID(用商品2, 不影响商品1的演示数据) */
    private static final long GOODS_ID = 2L;
    /** 压测前重置库存 */
    private static final int RESET_STOCK = 100;
    @Test
    public void loadTest() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        prepareData();
        List<String> tokens = loginAllUsers();
        System.out.println("===== 登录完成, 共 " + tokens.size() + " 个用户 =====");

        CountDownLatch ready = new CountDownLatch(tokens.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tokens.size());
        AtomicInteger queued = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        for (String token : tokens) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String body = doRequest("POST", "/seckill/doSeckill?goodsId=" + GOODS_ID, null, token);
                    if (body.contains("\"code\":200")) {
                        queued.incrementAndGet();
                    } else if (body.contains("抢完")) {
                        soldOut.incrementAndGet();
                    } else if (body.contains("频繁") || body.contains("重复提交")) {
                        limited.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(60, TimeUnit.SECONDS);
        long begin = System.currentTimeMillis();
        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - begin;
        pool.shutdown();

        System.out.println("\n========== 秒杀压测报告 ==========");
        System.out.println("并发线程数    : " + tokens.size());
        System.out.println("总耗时        : " + elapsed + " ms");
        System.out.println("端到端 QPS    : " + (tokens.size() * 1000L / Math.max(elapsed, 1)));
        System.out.println("成功入队      : " + queued.get());
        System.out.println("售罄失败      : " + soldOut.get());
        System.out.println("被限流        : " + limited.get());
        System.out.println("其他异常      : " + other.get());

        System.out.println("\n----- 等待MQ异步落库(3秒) -----");
        Thread.sleep(3000);
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT stock_count FROM seckill_goods WHERE goods_id=" + GOODS_ID)) {
                if (rs.next()) {
                    System.out.println("DB剩余库存    : " + rs.getInt(1));
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM seckill_order WHERE goods_id=" + GOODS_ID)) {
                if (rs.next()) {
                    System.out.println("DB秒杀订单数  : " + rs.getInt(1));
                }
            }
        }
        System.out.println("=================================");
    }
    /** 重置库存 + 清理压测数据 + 保证N个测试用户存在 */
    private void prepareData() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM seckill_order WHERE goods_id=" + GOODS_ID);
                st.executeUpdate("DELETE FROM order_info WHERE goods_id=" + GOODS_ID);
                st.executeUpdate("UPDATE seckill_goods SET stock_count=" + RESET_STOCK
                        + " WHERE goods_id=" + GOODS_ID);
            }
            String sql = "INSERT IGNORE INTO user(username, password, salt, nickname) "
                    + "VALUES(?, MD5(CONCAT('1qaz2wsx','123456')), '1qaz2wsx', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 1; i <= USERS; i++) {
                    ps.setString(1, "load" + i);
                    ps.setString(2, "压测用户" + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        System.out.println("数据准备完成: DB库存重置为 " + RESET_STOCK + ", 测试用户 load1~load" + USERS);
        System.out.println("提示: 压测前需重启应用触发Redis库存预热, 或执行 redis-cli set seckill:stock:"
                + GOODS_ID + " " + RESET_STOCK);
    }

    private List<String> loginAllUsers() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= USERS; i++) {
            String body = doRequest("POST", "/user/login",
                    "{\"username\":\"load" + i + "\",\"password\":\"123456\"}", null);
            int start = body.indexOf("\"data\":\"");
            if (start > 0) {
                int from = start + 8;
                int end = body.indexOf('"', from);
                if (end > from) {
                    tokens.add(body.substring(from, end));
                }
            }
        }
        return tokens;
    }

    private String doRequest(String method, String path, String json, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (json != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        conn.disconnect();
        return sb.toString();
    }
}
