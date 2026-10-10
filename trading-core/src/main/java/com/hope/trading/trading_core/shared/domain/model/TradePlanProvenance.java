package com.hope.trading.trading_core.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TradePlanProvenance(
        UUID opportunityId,
        long opportunityVersion,
        UUID strategyMatchId,
        UUID strategyId,
        Integer strategyVersion,
        UUID accountId,
        UUID sourceScanId,
        UUID sourceScanMarketId,
        UUID analysisExecutionId,
        UUID marketId
) {
    public TradePlanProvenance(
            UUID opportunityId, long opportunityVersion, UUID strategyMatchId,
            UUID strategyId, Integer strategyVersion) {
        this(opportunityId, opportunityVersion, strategyMatchId, strategyId, strategyVersion,
                null, null, null, null, null);
    }
    public TradePlanProvenance {
        Objects.requireNonNull(opportunityId, "opportunityId is required");
        if (opportunityVersion < 1) throw new IllegalArgumentException("opportunity version starts at 1");
        if (strategyVersion != null && strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
    }
}
