package com.hope.trading.market_data.model;

import java.util.UUID;

public record OrderBookKey(
        UUID marketId,
        int depth
) {
}
