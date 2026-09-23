package com.hope.trading.market_intelligence.adapter.marketdata;

import feign.RequestInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class MarketDataFeignConfiguration {
    private static final String ISSUER = "trading-core";
    private static final String AUDIENCE = "market-data";

    private final String secret;

    public MarketDataFeignConfiguration(
            @Value("${TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET:}") String secret) {
        this.secret = secret;
    }

    @Bean
    RequestInterceptor marketDataAuthorizationInterceptor() {
        return template -> {
            if (template.url().startsWith("/internal/")) {
                template.header("X-Service-Authorization", "Bearer " + issueToken());
            }
        };
    }

    private String issueToken() {
        if (secret.isBlank()) {
            throw new IllegalStateException(
                    "TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET is required for Market Data calls");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
