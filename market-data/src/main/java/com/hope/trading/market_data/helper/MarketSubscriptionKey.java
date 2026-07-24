package com.hope.trading.market_data.helper;

import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamType;

import java.util.UUID;

public record MarketSubscriptionKey(
        UUID marketId,
        MarketStreamType streamType,
        MarketStreamParameters parameters
) {
}
