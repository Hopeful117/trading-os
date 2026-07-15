package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface JwtService {

        String generateToken(UserDto userDto);

        String extractUsername(String token);

        UUID extractUserId(String token);

        String extractEmail(String token);

        Role extractRole(String token);

        boolean isTokenValid(String token);

        boolean isTokenExpired(String token);
    }


