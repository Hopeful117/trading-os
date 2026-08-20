package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record MarketEligibilityDecision(
        UUID marketId,
        String symbol,
        String provider,
        boolean eligible,
        List<MarketEligibilityReason> reasons
) {
}
