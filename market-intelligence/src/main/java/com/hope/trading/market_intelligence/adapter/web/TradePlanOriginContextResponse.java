package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.TradePlanOriginContext;
import java.time.Instant;
import java.util.UUID;

public record TradePlanOriginContextResponse(
        String originType,
        UUID accountId,
        UUID opportunityId,
        Long opportunityVersion,
        UUID marketId,
        UUID sourceScanId,
        String currentOpportunityStatus,
        Instant currentValidFrom,
        Instant currentValidUntil,
        boolean returnAvailable,
        String unavailableReason
) {
    static TradePlanOriginContextResponse from(TradePlanOriginContext context) {
        if (context == null) return null;
        return new TradePlanOriginContextResponse(
                context.originType(), context.accountId(), context.opportunityId(),
                context.opportunityVersion(), context.marketId(), context.sourceScanId(),
                context.currentOpportunityStatus(), context.currentValidFrom(),
                context.currentValidUntil(), context.returnAvailable(), context.unavailableReason());
    }

    public static TradePlanOriginContextResponse unavailable(
            String originType, UUID accountId, String reason) {
        return new TradePlanOriginContextResponse(
                originType, accountId, null, null, null, null, null, null, null, false, reason);
    }
}
