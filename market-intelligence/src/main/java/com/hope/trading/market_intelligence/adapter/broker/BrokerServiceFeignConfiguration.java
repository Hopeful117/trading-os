package com.hope.trading.market_intelligence.adapter.broker;

import feign.RequestInterceptor;
import feign.Request;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@SuppressWarnings("java:S2143")
public class BrokerServiceFeignConfiguration {
    private final String secret;

    public BrokerServiceFeignConfiguration(
            @Value("${MARKET_INTELLIGENCE_BROKER_SERVICE_JWT_SECRET:}") String secret) {
        this.secret = secret;
    }

    @Bean
    RequestInterceptor brokerServiceAuthorizationInterceptor() {
        return template -> template.header("X-Service-Authorization", "Bearer " + issueToken());
    }

    @Bean
    Request.Options brokerServiceRequestOptions(
            @Value("${MARKET_INTELLIGENCE_BROKER_SERVICE_CONNECT_TIMEOUT_MS:2000}") int connectTimeoutMs,
            @Value("${MARKET_INTELLIGENCE_BROKER_SERVICE_READ_TIMEOUT_MS:5000}") int readTimeoutMs) {
        return new Request.Options(connectTimeoutMs, readTimeoutMs);
    }

    private String issueToken() {
        if (secret.isBlank()) {
            throw new IllegalStateException(
                    "A Broker Service JWT secret is required for historical fee calls");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("market-intelligence")
                .subject("market-intelligence")
                .audience().add("broker-service").and()
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
