package com.hope.trading.market_data.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataWebSocketPropertiesTest {

    @Test
    void acceptsExplicitOrigins() {
        MarketDataWebSocketProperties properties = new MarketDataWebSocketProperties();
        properties.setAllowedOrigins(List.of("http://localhost:17085", "http://localhost:4200"));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsWildcardOrigin() {
        MarketDataWebSocketProperties properties = new MarketDataWebSocketProperties();
        properties.setAllowedOrigins(List.of("*"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Explicit Market Data WebSocket origins are required");
    }

    @Test
    void rejectsMissingOriginConfiguration() {
        MarketDataWebSocketProperties properties = new MarketDataWebSocketProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Explicit Market Data WebSocket origins are required");
    }

    @Test
    void rejectsOriginWithSurroundingWhitespace() {
        MarketDataWebSocketProperties properties = new MarketDataWebSocketProperties();
        properties.setAllowedOrigins(List.of(" http://localhost:17085"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Explicit Market Data WebSocket origins are required");
    }
}
