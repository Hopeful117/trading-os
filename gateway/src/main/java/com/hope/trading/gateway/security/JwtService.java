package com.hope.trading.gateway.security;


import com.hope.trading.gateway.dto.UserAuthenticationDto;
import com.hope.trading.gateway.helper.Role;

import java.util.UUID;

public interface JwtService {

        String generateToken(UserAuthenticationDto userAuthenticationDto);

        String extractUsername(String token);

        UUID extractUserId(String token);

        String extractEmail(String token);

        Role extractRole(String token);

        boolean isTokenValid(String token);

        boolean isTokenExpired(String token);
    }


