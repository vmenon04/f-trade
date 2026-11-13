package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.repository.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Long getUserIdByClientId(Long clientId) {
        return userMapper.findUserIdByClientId(clientId);
    }
}
