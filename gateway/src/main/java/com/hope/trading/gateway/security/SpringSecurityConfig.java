package com.hope.trading.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {
    private final JwtAuthenticationFilter authenticationFilter;
    private final AgentAuthenticationFilter agentAuthenticationFilter;

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchange -> exchange

                        .pathMatchers(
                                "/api/v1/users/login",
                                "/api/v1/users/register",
                                "/api/v1/broker-accounts/ctrader/authorization/callback",
                                "/api/v1/ctrader/authorization/callback",
                                "/api/v1/agents/token",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .anyExchange().authenticated()
                )

                .addFilterBefore(
                        agentAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .addFilterBefore(
                        authenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )

                .build();
    }
}
