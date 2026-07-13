package com.hope.trading.gateway.security;


import com.hope.trading.gateway.dto.UserAuthenticationDto;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

        String generateToken(UserAuthenticationDto userAuthenticationDto);

        String extractUsername(String token);

        boolean isTokenValid(String token, UserAuthenticationDto userAuthenticationDto);

        boolean isTokenExpired(String token);
    }


