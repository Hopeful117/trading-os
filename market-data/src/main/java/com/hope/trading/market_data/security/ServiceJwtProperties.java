package com.hope.trading.market_data.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.service-jwt")
public class ServiceJwtProperties {
    private boolean enabled;
    private String audience;
    private Map<String, String> trusted = new HashMap<>();
    private String authorizedCaller;
    private Set<String> authorizedCallers = Set.of();
    private Set<String> marketFactsAuthorizedCallers = Set.of();
    private Set<String> orderBookReplayAuthorizedCallers = Set.of();
    private Set<String> orderBookRetentionAuthorizedCallers = Set.of();

    public void validate() {
        if (!enabled) {
            return;
        }
        if (audience == null || audience.isBlank()
                || (authorizedCallers.isEmpty() && (authorizedCaller == null || authorizedCaller.isBlank()))
                || (authorizedCallers.isEmpty() && (trusted.get(authorizedCaller) == null
                || trusted.get(authorizedCaller).isBlank()))
                || authorizedCallers.stream().anyMatch(caller -> {
                    String secret = trusted.get(caller);
                    return secret == null || secret.isBlank();
                })
                || marketFactsAuthorizedCallers.stream().anyMatch(caller -> {
                    String secret = trusted.get(caller);
                    return secret == null || secret.isBlank();
                })
                || orderBookReplayAuthorizedCallers.stream().anyMatch(caller -> {
                    String secret = trusted.get(caller);
                    return secret == null || secret.isBlank();
                })
                || orderBookRetentionAuthorizedCallers.stream().anyMatch(caller -> {
                    String secret = trusted.get(caller);
                    return secret == null || secret.isBlank();
                })) {
            throw new IllegalStateException("Valid service JWT configuration is required");
        }
    }

    public boolean isAuthorizedCaller(String path, String caller) {
        if (path.startsWith("/internal/v1/market-facts/")
                && !marketFactsAuthorizedCallers.isEmpty()) {
            return marketFactsAuthorizedCallers.contains(caller);
        }
        if (path.startsWith("/internal/v1/order-book/replay/")
                && !orderBookReplayAuthorizedCallers.isEmpty()) {
            return orderBookReplayAuthorizedCallers.contains(caller);
        }
        if (path.startsWith("/internal/v1/order-book/retention/")
                && !orderBookRetentionAuthorizedCallers.isEmpty()) {
            return orderBookRetentionAuthorizedCallers.contains(caller);
        }
        return !authorizedCallers.isEmpty()
                ? authorizedCallers.contains(caller)
                : authorizedCaller.equals(caller);
    }
}
