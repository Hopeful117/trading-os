package com.hope.trading.market_intelligence.domain.marketstructure;

import java.util.List;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MarketStructureResult(
        java.util.UUID marketId, String provider, String symbol, String interval,
        Instant cutOffAt, String algorithmId, String ruleVersion, String policyId,
        String policyVersion, String parameterFingerprint, String inputFingerprint,
        MarketStructureAvailability availability, List<String> findings,
        List<MarketStructureSwing> retained, List<MarketStructureSwing> all,
        List<MarketStructureRelationEvidence> relations,
        String resultFingerprint) {
    public MarketStructureResult {
        Objects.requireNonNull(marketId); Objects.requireNonNull(provider);
        Objects.requireNonNull(symbol); Objects.requireNonNull(interval);
        Objects.requireNonNull(cutOffAt); Objects.requireNonNull(algorithmId);
        Objects.requireNonNull(ruleVersion); Objects.requireNonNull(policyId);
        Objects.requireNonNull(policyVersion); Objects.requireNonNull(parameterFingerprint);
        Objects.requireNonNull(inputFingerprint); Objects.requireNonNull(availability);
        findings = List.copyOf(findings); retained = List.copyOf(retained); all = List.copyOf(all);
        relations = List.copyOf(relations);
        Objects.requireNonNull(resultFingerprint);
    }
    public List<MarketStructureSwing> suppressed() {
        return all.stream().filter(MarketStructureSwing::suppressed).toList();
    }

    public Optional<MarketStructureRelationEvidence> latestRelation(
            MarketStructureSwingType type, Instant atOrBefore) {
        return relations.stream()
                .filter(value -> value.swingType() == type)
                .filter(value -> !value.previous().confirmationTime().isAfter(atOrBefore))
                .filter(value -> !value.latest().confirmationTime().isAfter(atOrBefore))
                .filter(value -> !value.latest().pivotTime().isAfter(atOrBefore))
                .reduce((first, second) -> second);
    }

    public Optional<MarketStructureRelationEvidence> latestRelationAfter(
            MarketStructureSwingType type, Instant after, Instant atOrBefore) {
        return relations.stream()
                .filter(value -> value.swingType() == type)
                .filter(value -> value.previous().pivotTime().isAfter(after))
                .filter(value -> value.latest().pivotTime().isAfter(after))
                .filter(value -> !value.previous().confirmationTime().isAfter(atOrBefore))
                .filter(value -> !value.latest().confirmationTime().isAfter(atOrBefore))
                .filter(value -> !value.latest().pivotTime().isAfter(atOrBefore))
                .reduce((first, second) -> second);
    }
}
