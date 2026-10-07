package com.hope.trading.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "news.provider")
public class NewsProviderProperties {
    private boolean enabled;
    private Duration maxAge = Duration.ofMinutes(15);
    private Map<String, String> attributions = Map.of();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration maxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }

    public Map<String, String> attributions() {
        return attributions;
    }

    public void setAttributions(Map<String, String> attributions) {
        this.attributions = attributions == null ? Map.of() : Map.copyOf(attributions);
    }
}
