package com.hope.trading.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public final class MarketDataServiceAuthorizationFilter implements GlobalFilter, Ordered {
    private static final String SERVICE_AUTHORIZATION = "X-Service-Authorization";
    private static final String MARKET_DATA_PATH = "/api/v1/markets/";
    private static final String SERVICE_NAME = "trading-core";
    private static final String AUDIENCE = "market-data";

    private final String signingSecret;

    public MarketDataServiceAuthorizationFilter(
            @Value("${security.market-data-service.secret:}") String secret) {
        this.signingSecret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!requiresServiceDelegation(exchange)) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove(SERVICE_AUTHORIZATION);
            headers.set(SERVICE_AUTHORIZATION, "Bearer " + issueToken());
        })).build());
    }

    private boolean requiresServiceDelegation(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        boolean subscription = path.matches(MARKET_DATA_PATH + "[^/]+/subscriptions");
        return subscription && (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                || HttpMethod.DELETE.equals(exchange.getRequest().getMethod()));
    }

    private String issueToken() {
        if (signingSecret.isBlank()) {
            throw new IllegalStateException("Market Data service JWT secret is not configured");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(SERVICE_NAME)
                .subject(SERVICE_NAME)
                .audience().add(AUDIENCE).and()
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingSecret)))
                .compact();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
