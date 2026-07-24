package com.hope.trading.gateway.security;

import com.hope.trading.gateway.dto.UserAuthenticationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WEBSOCKET_PATH_PREFIX = "/ws/";
    private static final String ACCESS_TOKEN_PARAMETER = "access_token";

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login"
    );

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {
        String path = exchange.getRequest()
                .getPath()
                .value();

        if (isPublicEndpoint(path)) {
            log.debug("Public endpoint accessed path={}", path);
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange);

        if (!StringUtils.hasText(token)) {
            log.debug(
                    "No authentication token provided path={}",
                    path
            );

            /*
             * La SecurityWebFilterChain décidera si la route
             * nécessite une authentification.
             */
            return chain.filter(exchange);
        }

        if (!jwtService.isTokenValid(token)) {
            log.warn(
                    "Invalid authentication token rejected path={}",
                    path
            );

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }

        Authentication authentication =
                buildAuthentication(token);

        return chain.filter(exchange)
                .contextWrite(
                        ReactiveSecurityContextHolder
                                .withAuthentication(authentication)
                );
    }

    private String resolveToken(ServerWebExchange exchange) {

        String authorizationHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authorizationHeader)
                && authorizationHeader.startsWith(BEARER_PREFIX)) {

            return authorizationHeader
                    .substring(BEARER_PREFIX.length())
                    .trim();
        }

        String path = exchange.getRequest()
                .getPath()
                .value();

        if (!path.startsWith(WEBSOCKET_PATH_PREFIX)) {
            return null;
        }

        String queryToken = exchange.getRequest()
                .getQueryParams()
                .getFirst(ACCESS_TOKEN_PARAMETER);

        if (!StringUtils.hasText(queryToken)) {
            return null;
        }

        /*
         * Accepte également une éventuelle valeur "Bearer xxx",
         * même si Angular doit normalement envoyer uniquement le JWT.
         */
        if (queryToken.startsWith(BEARER_PREFIX)) {
            return queryToken
                    .substring(BEARER_PREFIX.length())
                    .trim();
        }

        return queryToken.trim();
    }

    private Authentication buildAuthentication(String token) {

        UserAuthenticationDto principal =
                UserAuthenticationDto.builder()
                        .userId(jwtService.extractUserId(token))
                        .username(jwtService.extractUsername(token))
                        .email(jwtService.extractEmail(token))
                        .role(jwtService.extractRole(token))
                        .build();

        List<GrantedAuthority> authorities =
                List.of(
                        new SimpleGrantedAuthority(
                                principal.getRole().name()
                        )
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(endpoint ->
                        endpoint.equalsIgnoreCase(path)
                );
    }
}