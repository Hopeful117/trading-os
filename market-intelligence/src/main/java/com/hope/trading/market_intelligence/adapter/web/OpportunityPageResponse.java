package com.hope.trading.market_intelligence.adapter.web;

import java.util.List;

public record OpportunityPageResponse(
        List<OpportunityResponse> items, int page, int size, long total
) {
    public OpportunityPageResponse { items = List.copyOf(items); }
}
