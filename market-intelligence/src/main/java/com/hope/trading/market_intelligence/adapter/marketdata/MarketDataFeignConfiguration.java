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

@SuppressWarnings("java:S2143") // JJWT 0.x requires java.util.Date at this adapter boundary.
public class MarketDataFeignConfiguration {
    private static final String CORE_ISSUER = "trading-core";
    private static final String INTELLIGENCE_ISSUER = "market-intelligence";
    private static final String AUDIENCE = "market-data";

    private final String secret;
    private final String intelligenceSecret;

    public MarketDataFeignConfiguration(
            @Value("${TRADING_CORE_MARKET_DATA_SERVICE_JWT_SECRET:}") String secret,
            @Value("${MARKET_INTELLIGENCE_MARKET_DATA_SERVICE_JWT_SECRET:}") String intelligenceSecret) {
        this.secret = secret;
        this.intelligenceSecret = intelligenceSecret;
    }

    @Bean
    RequestInterceptor marketDataAuthorizationInterceptor() {
        return template -> {
            if (template.url().startsWith("/internal/")) {
                boolean facts = template.url().startsWith("/internal/v1/market-facts/");
                template.header("X-Service-Authorization", "Bearer " + issueToken(
                        facts ? intelligenceSecret : secret,
                        facts ? INTELLIGENCE_ISSUER : CORE_ISSUER));
            }
        };
    }

    private String issueToken(String signingSecret, String issuer) {
        if (signingSecret.isBlank()) {
            throw new IllegalStateException(
                    "A Market Data service JWT secret is required for internal Market Data calls");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(issuer)
                .audience().add(AUDIENCE).and()
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey(signingSecret))
                .compact();
    }

    private SecretKey signingKey(String signingSecret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingSecret));
    }
}
