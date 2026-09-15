package com.hope.trading.trading_core.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.service-jwt")
public class ServiceJwtProperties {
    private boolean enabled;
    private String name;
    private String secret;
    private long expirationSeconds = 60;
    private Map<String, String> trusted = new HashMap<>();
    private Map<String, String> audienceSecrets = new HashMap<>();

    public void validate() {
        if (!enabled) {
            return;
        }
        if (name == null || name.isBlank() || secret == null || secret.isBlank()
                || expirationSeconds <= 0 || expirationSeconds > 300
                || audienceSecrets.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("Valid service JWT configuration is required");
        }
    }

    public Map<String, String> getAudienceSecrets() { return audienceSecrets; }
    public void setAudienceSecrets(Map<String, String> audienceSecrets) { this.audienceSecrets = audienceSecrets; }
}
