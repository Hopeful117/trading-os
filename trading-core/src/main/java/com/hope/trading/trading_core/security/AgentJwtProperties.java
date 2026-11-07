package com.hope.trading.trading_core.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.agent-jwt")
public class AgentJwtProperties {
    private String secret;
    private String issuer;
    private String audience;
    private long expirationSeconds = 300;

    public void validate() {
        if (secret == null || secret.isBlank() || issuer == null || issuer.isBlank()
                || audience == null || audience.isBlank()
                || expirationSeconds <= 0 || expirationSeconds > 300) {
            throw new IllegalStateException("Valid agent JWT configuration is required");
        }
    }
}
