package com.example.seckill.service;

import cn.hutool.crypto.digest.MD5;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.common.BusinessException;
import com.example.seckill.entity.User;
import com.example.seckill.mapper.UserMapper;
import com.example.seckill.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 登录: 校验密码, 返回JWT token
     */
    public String login(String username, String password) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new BusinessException("用户名不存在");
        }
        // MD5(盐 + 密码) 与数据库存储的比对
        String encrypted = MD5.create().digestHex(user.getSalt() + password);
        if (!encrypted.equalsIgnoreCase(user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        return jwtUtil.createToken(user.getId(), user.getUsername());
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
