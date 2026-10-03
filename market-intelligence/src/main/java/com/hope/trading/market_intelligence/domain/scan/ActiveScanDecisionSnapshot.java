package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityDecision;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketFactsProvenance;

import java.util.List;
import java.util.UUID;

public record ActiveScanDecisionSnapshot(
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
    public ActiveScanDecisionSnapshot(UUID marketId, String symbol, String provider,
                                      boolean eligible, List<MarketEligibilityReason> reasons) {
        this(marketId, symbol, provider, eligible, reasons,
                eligible ? MarketEligibilityStatus.ELIGIBLE : MarketEligibilityStatus.EXCLUDED,
                null, null, null);
    }
    public ActiveScanDecisionSnapshot {
        reasons = List.copyOf(reasons);
    }

    public static ActiveScanDecisionSnapshot from(MarketEligibilityDecision decision) {
        return new ActiveScanDecisionSnapshot(
                decision.marketId(),
                decision.symbol(),
                decision.provider(),
                decision.eligible(),
                decision.reasons(),
                decision.status(),
                decision.marketFactsStatus(),
                decision.marketFactsCalculationVersion(),
                decision.marketFactsProvenance()
        );
    }
}
