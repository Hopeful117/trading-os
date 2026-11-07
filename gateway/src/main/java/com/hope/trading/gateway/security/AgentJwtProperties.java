package com.hope.trading.gateway.security;

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

    public void validate() {
        if (secret == null || secret.isBlank() || issuer == null || issuer.isBlank()
                || audience == null || audience.isBlank()) {
            throw new IllegalStateException("Valid agent JWT configuration is required");
        }
    }
}
