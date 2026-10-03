package com.hope.trading.market_intelligence.domain.scan;

import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionResult;
import com.hope.trading.market_intelligence.domain.scope.MarketScopeMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanScopeSnapshot(
        MarketScopeMode scopeMode,
        String policyName,
        String policyVersion,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<ActiveScanDecisionSnapshot> decisions,
        List<UUID> effectiveMarketIds,
        Instant resolvedAt,
        Instant assessmentCutoff,
        List<String> ruleVersions
) {
    public ActiveScanScopeSnapshot {
        requestedMarketIds = List.copyOf(requestedMarketIds);
        candidateMarketIds = List.copyOf(candidateMarketIds);
        decisions = List.copyOf(decisions);
        effectiveMarketIds = List.copyOf(effectiveMarketIds);
        ruleVersions = ruleVersions == null ? List.of() : List.copyOf(ruleVersions);
    }

    public ActiveScanScopeSnapshot(List<UUID> requestedMarketIds, List<UUID> candidateMarketIds,
                                   List<ActiveScanDecisionSnapshot> decisions,
                                   List<UUID> effectiveMarketIds, Instant resolvedAt) {
        this(MarketScopeMode.SELECTED, null, null, requestedMarketIds, candidateMarketIds, decisions,
                effectiveMarketIds, resolvedAt, resolvedAt, List.of());
    }

    public static ActiveScanScopeSnapshot from(ActiveScanScopeResolutionResult result) {
        return new ActiveScanScopeSnapshot(
                result.scopeMode(),
                result.policyName(),
                result.policyVersion(),
                result.requestedMarketIds(),
                result.candidateMarketIds(),
                result.decisions().stream().map(ActiveScanDecisionSnapshot::from).toList(),
                result.effectiveScope().marketIds(),
                result.resolvedAt(),
                result.assessmentCutoff(),
                result.ruleVersions()
        );
    }
}
