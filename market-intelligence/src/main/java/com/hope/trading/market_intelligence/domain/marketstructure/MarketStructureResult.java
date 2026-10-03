package com.hope.trading.market_intelligence.domain.marketstructure;

import java.util.List;
import java.time.Instant;
import java.util.Objects;

public record MarketStructureResult(
        java.util.UUID marketId, String provider, String symbol, String interval,
        Instant cutOffAt, String algorithmId, String ruleVersion, String policyId,
        String policyVersion, String parameterFingerprint, String inputFingerprint,
        MarketStructureAvailability availability, List<String> findings,
        List<MarketStructureSwing> retained, List<MarketStructureSwing> all,
        String resultFingerprint) {
    public MarketStructureResult {
        Objects.requireNonNull(marketId); Objects.requireNonNull(provider);
        Objects.requireNonNull(symbol); Objects.requireNonNull(interval);
        Objects.requireNonNull(cutOffAt); Objects.requireNonNull(algorithmId);
        Objects.requireNonNull(ruleVersion); Objects.requireNonNull(policyId);
        Objects.requireNonNull(policyVersion); Objects.requireNonNull(parameterFingerprint);
        Objects.requireNonNull(inputFingerprint); Objects.requireNonNull(availability);
        findings = List.copyOf(findings); retained = List.copyOf(retained); all = List.copyOf(all);
        Objects.requireNonNull(resultFingerprint);
    }
    public List<MarketStructureSwing> suppressed() {
        return all.stream().filter(MarketStructureSwing::suppressed).toList();
    }
}
