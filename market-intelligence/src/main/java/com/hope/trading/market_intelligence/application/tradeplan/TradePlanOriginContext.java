package com.hope.trading.market_intelligence.application.tradeplan;

import java.time.Instant;
import java.util.UUID;

public record TradePlanOriginContext(
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
    public static TradePlanOriginContext unavailable(
            String originType, UUID accountId, String reason) {
        return new TradePlanOriginContext(
                originType, accountId, null, null, null, null, null, null, null, false, reason);
    }
}
