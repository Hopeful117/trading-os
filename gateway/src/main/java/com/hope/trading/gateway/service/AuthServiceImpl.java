package com.hope.trading.gateway.service;

import com.hope.trading.gateway.dto.AuthenticationResponse;
import com.hope.trading.gateway.dto.LoginRequest;
import com.hope.trading.gateway.dto.UserAuthenticationDto;
import com.hope.trading.gateway.feignClient.UserClient;
import com.hope.trading.gateway.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements  AuthService {
    private final UserClient userClient;
    private final JwtService jwtService;


    @Override
    public AuthenticationResponse login(LoginRequest request) {

        UserAuthenticationDto user=userClient.authenticate(request);


        String token =
                jwtService.generateToken(user);


        return AuthenticationResponse.builder()
                .token(token)
                .build();

    }

    }

