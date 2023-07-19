package com.cloud.service.impl;

import com.cloud.mapper.UserMapper;
import com.cloud.service.UserService;
import com.entity.User;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service("userService")
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User getUserById(Integer uid) {
        return userMapper.getUserById(uid);
    }

    @Override
    public Integer getRemain(Integer uid) {
        return userMapper.getUserBookRemain(uid);
    }

    @Override
    public boolean setRemain(Integer uid, Integer count) {
        return userMapper.updateBookCount(uid, uid) > 0;
    }

}
