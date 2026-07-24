package com.hope.trading.market_data.model;

import java.util.UUID;

public record MarketStreamRequest(
        MarketStreamType type,
        MarketStreamParameters parameters
) {

}
