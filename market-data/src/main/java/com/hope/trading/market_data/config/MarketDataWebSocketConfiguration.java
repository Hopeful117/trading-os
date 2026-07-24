package com.hope.trading.market_data.config;

import com.hope.trading.market_data.service.MarketDataWebSocketHandler;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;

import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MarketDataWebSocketConfiguration implements WebSocketConfigurer {
    private final MarketDataWebSocketHandler marketDataWebSocketHandler;


    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {
        registry.addHandler(
                        marketDataWebSocketHandler,
                        "/ws/market-data"
                )
                .setAllowedOriginPatterns("*");
    }
}