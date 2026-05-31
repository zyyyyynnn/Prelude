package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.BusinessException;
import com.interview.dto.LoginRequest;
import com.interview.dto.LoginResponse;
import com.interview.dto.RegisterRequest;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import com.interview.service.AuthService;
import com.interview.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw BusinessException.badRequest("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        userMapper.insert(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, request.getUsername())
            .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("用户名或密码错误");
        }
        return new LoginResponse(jwtUtil.generateToken(user.getId()));
    }
}
