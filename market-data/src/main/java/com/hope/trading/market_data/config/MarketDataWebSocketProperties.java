package com.hope.trading.market_data.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "market-data.websocket")
public class MarketDataWebSocketProperties {
    private List<String> allowedOrigins = List.of();

    public void validate() {
        if (allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(origin ->
                origin == null || origin.isBlank() || !origin.equals(origin.trim()))
                || allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException(
                    "Explicit Market Data WebSocket origins are required");
        }
    }
}
