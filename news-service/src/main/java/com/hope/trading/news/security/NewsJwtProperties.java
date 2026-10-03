package com.hope.trading.news.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class NewsJwtProperties {
    private String secret;
    private String issuer;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank() || "default-secret-must-change".equals(secret)) {
            throw new IllegalStateException("A non-default JWT secret is required");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("A JWT issuer is required");
        }
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
