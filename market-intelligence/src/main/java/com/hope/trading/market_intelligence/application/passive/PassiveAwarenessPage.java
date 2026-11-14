package com.hope.trading.market_intelligence.application.passive;

import java.util.List;

public record PassiveAwarenessPage(
        List<PassiveAwarenessProjection> items,
        int page,
        int size,
        long total,
        boolean truncated
) {
    public PassiveAwarenessPage {
        items = List.copyOf(items);
    }
}
