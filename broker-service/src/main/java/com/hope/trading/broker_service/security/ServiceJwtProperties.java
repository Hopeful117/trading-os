package com.hope.trading.broker_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@ConfigurationProperties(prefix = "security.service-jwt")
public class ServiceJwtProperties {
    private boolean enabled;
    private String name;
    private String secret;
    private long expirationSeconds = 60;
    private Map<String, String> trusted = new HashMap<>();
    private Set<String> authorizedCallers = new HashSet<>();

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String secret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long expirationSeconds() { return expirationSeconds; }
    public void setExpirationSeconds(long value) { this.expirationSeconds = value; }
    public Map<String, String> trusted() { return trusted; }
    public void setTrusted(Map<String, String> trusted) { this.trusted = trusted; }
    public Set<String> authorizedCallers() { return authorizedCallers; }
    public void setAuthorizedCallers(Set<String> authorizedCallers) { this.authorizedCallers = authorizedCallers; }

    public void validate() {
        if (enabled && (name == null || name.isBlank() || secret == null || secret.isBlank()
                || expirationSeconds <= 0 || expirationSeconds > 300)) {
            throw new IllegalStateException("Valid service JWT configuration is required");
        }
    }
}
