package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record MarketEligibilityDecision(
        UUID marketId,
        String symbol,
        String provider,
        boolean eligible,
        List<MarketEligibilityReason> reasons,
        MarketEligibilityStatus status,
        String marketFactsStatus,
        String marketFactsCalculationVersion,
        MarketFactsProvenance marketFactsProvenance
) {
    public MarketEligibilityDecision(
            UUID marketId,
            String symbol,
            String provider,
            boolean eligible,
            List<MarketEligibilityReason> reasons
    ) {
        this(marketId, symbol, provider, eligible,
                reasons,
                eligible ? MarketEligibilityStatus.ELIGIBLE : MarketEligibilityStatus.EXCLUDED,
                null,
                null,
                null);
    }

    public MarketEligibilityDecision {
        reasons = List.copyOf(reasons);
        if (status == null) {
            status = eligible ? MarketEligibilityStatus.ELIGIBLE : MarketEligibilityStatus.EXCLUDED;
        }
    }
}
