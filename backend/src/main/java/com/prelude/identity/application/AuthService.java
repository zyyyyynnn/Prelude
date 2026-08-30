package com.prelude.identity.application;

import com.prelude.identity.api.LoginRequest;
import com.prelude.identity.api.LoginResponse;
import com.prelude.identity.api.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
