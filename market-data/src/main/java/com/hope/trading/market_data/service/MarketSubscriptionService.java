package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.MarketStreamRequest;

import java.util.UUID;
import java.time.Duration;
import java.util.List;

public interface MarketSubscriptionService {
    void subscribe(
            UUID marketId,
            MarketStreamRequest request
    );

    default void subscribe(List<UUID> marketIds, MarketStreamRequest request, Duration timeout) {
        marketIds.forEach(marketId -> subscribe(marketId, request, timeout));
    }

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
