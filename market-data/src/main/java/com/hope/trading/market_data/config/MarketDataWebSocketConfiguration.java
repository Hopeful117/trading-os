package com.hope.trading.market_data.config;

import com.hope.trading.market_data.service.MarketDataWebSocketHandler;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Map;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(MarketDataWebSocketProperties.class)
@RequiredArgsConstructor
public class MarketDataWebSocketConfiguration implements WebSocketConfigurer {
    private final MarketDataWebSocketHandler marketDataWebSocketHandler;
    private final MarketDataWebSocketProperties properties;


    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {
        properties.validate();
        registry.addHandler(
                        marketDataWebSocketHandler,
                        "/ws/market-data"
                )
                .setAllowedOrigins(properties.getAllowedOrigins().toArray(String[]::new));
    }
}
