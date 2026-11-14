package com.hope.trading.market_intelligence.adapter.web;

import java.util.List;

public record PassiveAwarenessPageResponse(
        List<PassiveAwarenessResponse> items,
        int page,
        int size,
        long total,
        boolean truncated
) {
    public PassiveAwarenessPageResponse {
        items = List.copyOf(items);
    }
}
