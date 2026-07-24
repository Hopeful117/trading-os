package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;

import java.util.UUID;

public interface MarketSubscriptionService {
    void subscribe(
            UUID marketId,
            MarketStreamRequest request
    );

    void unsubscribe(
            UUID marketId,
            MarketStreamRequest request
    );
}
