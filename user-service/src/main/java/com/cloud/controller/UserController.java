package com.cloud.controller;

import com.cloud.service.UserService;
import com.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class UserController {

    @Resource
    private UserService userService;

    private int userCallCount = 0;

    @GetMapping("/api/user/{uid}")
    public User findUserById(@PathVariable("uid") Integer uid) {

        int count = userCallCount++; System.err.println("调用了用户服务" + count + "次");
        return userService.getUserById(uid);

    }

}
