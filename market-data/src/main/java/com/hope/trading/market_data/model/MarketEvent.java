package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;

import java.time.Instant;
import java.util.UUID;

public interface MarketEvent {
    UUID marketId();

    MarketProvider provider();

    String symbol();

    MarketStreamType streamType();

    Instant occurredAt();
}

