package com.hope.trading.trading_core.security;

import com.hope.trading.trading_core.dto.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login"
    );
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


            String path = request.getRequestURI();

            boolean isPublic = PUBLIC_ENDPOINTS.stream()
                    .anyMatch(path::equalsIgnoreCase);

            if (isPublic) {
                log.info("Accessing public path");
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.info("No token detected");
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            if (!jwtService.isTokenValid(token)) {
                log.error("Invalid token");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UserDto principal =
                    UserDto.builder()
                            .userId(jwtService.extractUserId(token))
                            .username(jwtService.extractUsername(token))
                            .email(jwtService.extractEmail(token))
                            .role(jwtService.extractRole(token))
                            .build();

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority(principal.getRole().name())
            );

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            filterChain.doFilter(request, response);
        }

    }

