package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Instant;
import java.util.UUID;

public final class OrderBookReplayIdentity {
    private static final String VERSION = "order-book-replay:v1";

    private OrderBookReplayIdentity() {
    }

    public static String forRequest(UUID marketId, int depth, Instant cutoff) {
        if (marketId == null || depth < 1 || cutoff == null) {
            throw new IllegalArgumentException("marketId, depth and cutoff are required");
        }
        return VERSION + ":market=" + marketId + ":depth=" + depth + ":cutoff=" + cutoff;
    }
}
