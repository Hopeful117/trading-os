package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.MarketStreamRequest;

import java.util.UUID;
import java.time.Duration;

public interface MarketSubscriptionService {
    void subscribe(
            UUID marketId,
            MarketStreamRequest request
    );

    void unsubscribe(
            UUID marketId,
            MarketStreamRequest request
    );

    default void subscribe(UUID marketId, MarketStreamRequest request, Duration timeout) {
        subscribe(marketId, request);
    }

    default void unsubscribe(UUID marketId, MarketStreamRequest request, Duration timeout) {
        unsubscribe(marketId, request);
    }
}
