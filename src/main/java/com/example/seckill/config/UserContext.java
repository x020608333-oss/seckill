package com.example.seckill.config;

import com.example.seckill.entity.User;

/**
 * 用ThreadLocal保存当前请求的用户, 避免层层传参
 * 拦截器在请求开始时set, 请求结束时remove
 */
public class UserContext {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    public static User getUser() {
        return USER_HOLDER.get();
    }

    public static void remove() {
        USER_HOLDER.remove();
    }
}
