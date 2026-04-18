package com.interview.service;

import com.interview.dto.LoginRequest;
import com.interview.dto.LoginResponse;
import com.interview.dto.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
