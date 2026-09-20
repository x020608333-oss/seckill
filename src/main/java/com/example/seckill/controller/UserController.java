package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.service.UserService;
import com.example.seckill.vo.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 登录, 返回JWT
     * 测试账号: admin / 123456
     */
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginVo loginVo) {
        String token = userService.login(loginVo.getUsername(), loginVo.getPassword());
        return Result.success(token);
    }
}
