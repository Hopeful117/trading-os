package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityDecision;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;

import java.util.List;
import java.util.UUID;

public record ActiveScanDecisionSnapshot(
        UUID marketId,
        String symbol,
        String provider,
        boolean eligible,
        List<MarketEligibilityReason> reasons
) {
    public ActiveScanDecisionSnapshot {
        reasons = List.copyOf(reasons);
    }

    public static ActiveScanDecisionSnapshot from(MarketEligibilityDecision decision) {
        return new ActiveScanDecisionSnapshot(
                decision.marketId(),
                decision.symbol(),
                decision.provider(),
                decision.eligible(),
                decision.reasons()
        );
    }
}
