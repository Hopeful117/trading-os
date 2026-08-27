package com.hope.trading.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Public routing contract of Trading OS (STORY-0019).
 *
 * <p>Convention: each exposed domain owns its complete public prefix
 * ({@code /api/v1/<domain>/**}) and the Gateway routes by prefix without any
 * path rewrite. Internal {@code /internal/**} endpoints are deliberately NOT
 * routed.</p>
 *
 * <p>{@code gateway.targets.<service>} may override the default
 * {@code lb://<service>} target — used by integration tests to prove real
 * HTTP request → Gateway → downstream routing without service discovery.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayRouteConfig {

    private final Map<String, String> targets = new HashMap<>();

    public Map<String, String> getTargets() {
        return targets;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return marketRoutes(builder,
                (service, defaultUri) -> targets.getOrDefault(service, defaultUri));
    }

    /** Single source of truth for the public route table. */
    static RouteLocator marketRoutes(
            RouteLocatorBuilder builder,
            java.util.function.BiFunction<String, String, String> uriFor) {
        return builder.routes()

                .route("authentication", r -> r
                        .path("/api/v1/users/**")
                        .uri(uriFor.apply("trading-core", "lb://trading-core"))
                )
                .route("accounts", r -> r
                        .path("/api/v1/accounts/**")
                        .uri(uriFor.apply("trading-core", "lb://trading-core"))
                 )
                .route("broker-credential-commands", r -> r
                        .path(
                                "/api/v1/broker-accounts/*/credentials",
                                "/api/v1/broker-accounts/*/validate",
                                "/api/v1/broker-accounts/*/connection-status"
                        )
                        .uri(uriFor.apply("broker-service", "lb://broker-service"))
                )
                .route("broker-accounts", r -> r
                        .path("/api/v1/broker-accounts/**")
                        .uri(uriFor.apply("trading-core", "lb://trading-core"))
                )
                .route("trade-plan-risk-evaluations", r -> r
                        // Covers public TradePlan orchestration and creation
                        // (/api/v1/trade-plans/analyses/**) owned by Trading Core.
                        .path("/api/v1/trade-plans/**")
                        .uri(uriFor.apply("trading-core", "lb://trading-core"))
                )
                .route("opportunities", r -> r
                        // Market Intelligence trader-facing opportunity API.
                        // Deliberately a dedicated prefix so it can never be
                        // captured by the intelligence route again (STORY-0019).
                        .path("/api/v1/opportunities/**")
                        .uri(uriFor.apply("market-intelligence", "lb://market-intelligence"))
                )
                .route("markets", r -> r
                        .path("/api/v1/markets/**")
                                .uri(uriFor.apply("market-data", "lb://market-data"))
                )
                .route("markets-data-websocket", r -> r
                        .path("/ws/market-data")
                        .uri(uriFor.apply("market-data-ws", "lb:ws://market-data"))
                )
                .route("market-intelligence", r -> r
                        .path("/api/v1/intelligence/**")
                        .uri(uriFor.apply("market-intelligence", "lb://market-intelligence"))
                )
                .route("executions", r -> r
                        .path("/api/v1/executions/**")
                        .uri(uriFor.apply("trading-core", "lb://trading-core"))
                )

                .build();
    }
}
