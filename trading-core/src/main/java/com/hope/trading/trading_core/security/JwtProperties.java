package com.hope.trading.trading_core.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;

@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
@RequiredArgsConstructor
public class JwtProperties {
        private final  String secret;
        private final Long expiration;
        private final String issuer;

        @PostConstruct
        void validate() {
                if (secret == null || secret.isBlank()
                        || "default-secret-must-change".equals(secret)) {
                        throw new IllegalStateException("A non-default JWT secret is required");
                }
                if (expiration == null || expiration <= 0 || issuer == null || issuer.isBlank()) {
                        throw new IllegalStateException("Valid JWT expiration and issuer are required");
                }
        }
}
