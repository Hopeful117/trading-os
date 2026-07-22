package com.hope.trading.gateway.security;

import com.hope.trading.gateway.dto.UserAuthenticationDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.SpringCacheAnnotationParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {
    private final JwtService jwtService;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        boolean isPublic = PUBLIC_ENDPOINTS.stream()
                .anyMatch(endpoint -> endpoint.equalsIgnoreCase(path));
        if (isPublic  ) {
            log.debug("Public endpoint accessed path={}", path);
            return chain.filter(exchange);
        }
        String authHeader =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No bearer token provided path={}", path);
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("Invalid bearer token rejected path={}", path);
            return exchange.getResponse().setComplete();
        }

        UserAuthenticationDto principal =
                UserAuthenticationDto.builder()
                        .userId(jwtService.extractUserId(token))
                        .username(jwtService.extractUsername(token))
                        .email(jwtService.extractEmail(token))
                        .role(jwtService.extractRole(token))
                        .build();

        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(principal.getRole().name()));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                );
    }
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login"
    );
}
