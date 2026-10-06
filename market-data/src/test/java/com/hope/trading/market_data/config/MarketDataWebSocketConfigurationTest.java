package com.hope.trading.market_data.config;

import com.hope.trading.market_data.service.MarketDataWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataWebSocketConfigurationTest {

    @Test
    void registersConfiguredOriginsWithoutWildcard() {
        MarketDataWebSocketHandler handler = mock(MarketDataWebSocketHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws/market-data")).thenReturn(registration);

        MarketDataWebSocketProperties properties = new MarketDataWebSocketProperties();
        properties.setAllowedOrigins(List.of("http://localhost:17085", "http://localhost:4200"));

        new MarketDataWebSocketConfiguration(handler, properties)
                .registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins(
                "http://localhost:17085", "http://localhost:4200");
    }
}
