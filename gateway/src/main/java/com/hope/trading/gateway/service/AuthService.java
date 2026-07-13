package com.hope.trading.gateway.service;

import com.hope.trading.gateway.dto.AuthenticationResponse;
import com.hope.trading.gateway.dto.LoginRequest;

public interface AuthService {
    AuthenticationResponse login(LoginRequest request);
}
