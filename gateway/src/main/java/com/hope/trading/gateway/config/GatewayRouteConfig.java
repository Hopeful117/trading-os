package com.hope.trading.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("authentication", r -> r
                        .path("/api/v1/users/**")
                        .uri("lb://trading-core")
                )
                .route("accounts",r -> r
                        .path("/api/v1/accounts/**")
                        .uri("lb://trading-core")
                 )
                .route("broker-credential-commands", r -> r
                        .path(
                                "/api/v1/broker-accounts/*/credentials",
                                "/api/v1/broker-accounts/*/validate",
                                "/api/v1/broker-accounts/*/connection-status"
                        )
                        .uri("lb://broker-service")
                )
                .route("broker-accounts", r -> r
                        .path("/api/v1/broker-accounts/**")
                        .uri("lb://trading-core")
                )
                .route("trade-plan-risk-evaluations", r -> r
                        .path("/api/v1/trade-plans/**")
                        .uri("lb://trading-core")
                )
                .route("markets", r -> r
                        .path("/api/v1/markets/**")
                                .uri("lb://market-data")
                )
                .route("markets-data-websocket",r->r
                        .path("/ws/market-data")
                        .uri("lb:ws://market-data"))
                .route("market-intelligence", r -> r
                        .path("/api/v1/intelligence/**")
                        .uri("lb://market-intelligence"))


                .build();
    }
}
