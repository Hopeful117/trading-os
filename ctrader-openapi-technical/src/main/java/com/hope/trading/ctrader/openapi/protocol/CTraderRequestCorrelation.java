package com.hope.trading.ctrader.openapi.protocol;

import java.util.UUID;

public record CTraderRequestCorrelation(String clientMessageId) {
    public CTraderRequestCorrelation {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new IllegalArgumentException("clientMessageId is required");
        }
    }

    public static CTraderRequestCorrelation create() {
        return new CTraderRequestCorrelation(UUID.randomUUID().toString());
    }
}
