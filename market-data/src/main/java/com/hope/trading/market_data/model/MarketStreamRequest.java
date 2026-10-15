package com.hope.trading.market_data.model;

public record MarketStreamRequest(
        MarketStreamType type,
        MarketStreamParameters parameters
) {

}
