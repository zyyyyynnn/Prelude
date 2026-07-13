package com.interview.identity.application;

import com.interview.identity.api.LoginRequest;
import com.interview.identity.api.LoginResponse;
import com.interview.identity.api.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
