package com.hope.trading.trading_core.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TradePlanProvenance(
        UUID opportunityId,
        long opportunityVersion,
        UUID strategyMatchId,
        UUID strategyId,
        Integer strategyVersion
) {
    public TradePlanProvenance {
        Objects.requireNonNull(opportunityId, "opportunityId is required");
        if (opportunityVersion < 1) throw new IllegalArgumentException("opportunity version starts at 1");
        if (strategyVersion != null && strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
    }
}
