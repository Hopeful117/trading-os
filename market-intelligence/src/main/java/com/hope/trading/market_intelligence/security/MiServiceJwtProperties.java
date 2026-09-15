package com.hope.trading.market_intelligence.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "security.service-jwt")
public class MiServiceJwtProperties {
    private boolean enabled;
    private String audience = "market-intelligence";
    private Map<String, String> trusted = new HashMap<>();
    private Set<String> authorizedCallers = new HashSet<>();

    @PostConstruct
    void validate() {
        if (enabled && (audience == null || audience.isBlank()
                || trusted.isEmpty() || trusted.values().stream().anyMatch(value -> value == null || value.isBlank())
                || authorizedCallers.isEmpty())) {
            throw new IllegalStateException("Valid MI service JWT configuration is required");
        }
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String audience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Map<String, String> trusted() { return trusted; }
    public void setTrusted(Map<String, String> trusted) { this.trusted = trusted; }
    public Set<String> authorizedCallers() { return authorizedCallers; }
    public void setAuthorizedCallers(Set<String> authorizedCallers) { this.authorizedCallers = authorizedCallers; }
}
