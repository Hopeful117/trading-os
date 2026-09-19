package com.hope.trading.market_data.security;

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
    private String audience;
    private Map<String, String> trusted = new HashMap<>();
    private String authorizedCaller;

    public void validate() {
        if (!enabled) {
            return;
        }
        if (audience == null || audience.isBlank()
                || authorizedCaller == null || authorizedCaller.isBlank()
                || trusted.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("Valid service JWT configuration is required");
        }
    }
}
