package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanScopeSnapshot(
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<ActiveScanDecisionSnapshot> decisions,
        List<UUID> effectiveMarketIds,
        Instant resolvedAt
) {
    public ActiveScanScopeSnapshot {
        requestedMarketIds = List.copyOf(requestedMarketIds);
        candidateMarketIds = List.copyOf(candidateMarketIds);
        decisions = List.copyOf(decisions);
        effectiveMarketIds = List.copyOf(effectiveMarketIds);
    }

    public static ActiveScanScopeSnapshot from(ActiveScanScopeResolutionResult result) {
        return new ActiveScanScopeSnapshot(
                result.requestedMarketIds(),
                result.candidateMarketIds(),
                result.decisions().stream().map(ActiveScanDecisionSnapshot::from).toList(),
                result.effectiveScope().marketIds(),
                result.resolvedAt()
        );
    }
}
