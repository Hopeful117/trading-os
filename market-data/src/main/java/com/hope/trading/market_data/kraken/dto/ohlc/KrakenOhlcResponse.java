package com.hope.trading.market_data.kraken.dto.ohlc;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record KrakenOhlcResponse(
        List<String> error,
        JsonNode result

) {
}
