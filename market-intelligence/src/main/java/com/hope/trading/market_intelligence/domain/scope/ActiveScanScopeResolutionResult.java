package com.hope.trading.market_intelligence.domain.scope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionResult(
        UUID accountId,
        String objective,
        MarketScopeMode scopeMode,
        String policyName,
        String policyVersion,
        List<UUID> requestedMarketIds,
        List<UUID> candidateMarketIds,
        List<MarketEligibilityDecision> decisions,
        EffectiveScanScope effectiveScope,
        Instant resolvedAt,
        Instant assessmentCutoff,
        List<String> ruleVersions
) {
    public ActiveScanScopeResolutionResult(
            UUID accountId, String objective, List<UUID> requestedMarketIds,
            List<UUID> candidateMarketIds, List<MarketEligibilityDecision> decisions,
            EffectiveScanScope effectiveScope, Instant resolvedAt) {
        this(accountId, objective, MarketScopeMode.SELECTED, null, null, requestedMarketIds,
                candidateMarketIds, decisions, effectiveScope, resolvedAt, resolvedAt, List.of());
    }

    public ActiveScanScopeResolutionResult {
        ruleVersions = ruleVersions == null ? List.of() : List.copyOf(ruleVersions);
    }
}
