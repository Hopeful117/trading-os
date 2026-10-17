package com.hope.trading.market_intelligence.adapter.tradingcore;

import feign.RequestInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@SuppressWarnings("java:S2143")
public class EconomicResearchFeignConfiguration {
    private final String secret;

    public EconomicResearchFeignConfiguration(
            @Value("${MARKET_INTELLIGENCE_SERVICE_JWT_SECRET:}") String secret) {
        this.secret = secret;
    }

    @Bean
    RequestInterceptor economicResearchAuthorizationInterceptor() {
        return template -> template.header("X-Service-Authorization", "Bearer " + issueToken());
    }

    private String issueToken() {
        if (secret.isBlank()) {
            throw new IllegalStateException("A Trading Core service JWT secret is required for economic research calls");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("market-intelligence")
                .subject("market-intelligence")
                .audience().add("trading-core").and()
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }
}
