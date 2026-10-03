package com.hope.trading.market_intelligence.domain.marketstructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, provider-neutral confirmed swing extraction. */
public final class MarketStructureEngine {
    public MarketStructureResult extract(MarketStructureInput input) {
        if (input.evidenceStatus() != MarketStructureEvidenceStatus.COMPLETE) {
            return result(input, availability(input.evidenceStatus()), List.of(), List.of(), List.of(), input.findings());
        }
        List<MarketStructureCandle> sorted = input.candles().stream()
                .sorted(Comparator.comparing(MarketStructureCandle::closeTime)
                        .thenComparing(MarketStructureCandle::sourceId)).toList();
        List<MarketStructureCandle> candles = new ArrayList<>();
        for (MarketStructureCandle candle : sorted) {
            MarketStructureCandle previous = candles.stream()
                    .filter(value -> value.openTime().equals(candle.openTime())).findFirst().orElse(null);
            if (previous == null) candles.add(candle);
            else if (!sameEvidence(previous, candle)) {
                return result(input, MarketStructureAvailability.INVALID, List.of(), List.of(), List.of(),
                        List.of("DUPLICATE_CONFLICT:" + candle.openTime()));
            }
        }
        long eligibleCount = candles.stream().filter(c -> c.closed() && !c.synthetic()
                && !c.closeTime().isAfter(input.cutOffAt())).count();
        if (eligibleCount < input.pivotRadius() * 2L + 1) {
            return result(input, MarketStructureAvailability.INSUFFICIENT, List.of(), List.of(), List.of(),
                    List.of("INSUFFICIENT_HISTORY"));
        }
        List<MarketStructureSwing> candidates = new ArrayList<>();
        int radius = input.pivotRadius();
        for (int i = radius; i < candles.size() - radius; i++) {
            MarketStructureCandle pivot = candles.get(i);
            boolean high = true, low = true;
            for (int j = i - radius; j <= i + radius; j++) if (j != i) {
                high &= pivot.high().compareTo(candles.get(j).high()) > 0;
                low &= pivot.low().compareTo(candles.get(j).low()) < 0;
            }
            var confirmation = candles.get(i + radius).closeTime();
            if (confirmation.isAfter(input.cutOffAt()) || !eligibleWindow(candles, i, radius, input.cutOffAt())
                    || intersectsGap(input, candles, i, radius)) continue;
            if (high) candidates.add(swing(input, candles, i, MarketStructureSwingType.HIGH, pivot.high(), confirmation));
            if (low) candidates.add(swing(input, candles, i, MarketStructureSwingType.LOW, pivot.low(), confirmation));
        }
        candidates.sort(Comparator.comparing(MarketStructureSwing::pivotTime)
                .thenComparing(MarketStructureSwing::type));
        List<MarketStructureSwing> retained = new ArrayList<>(), all = new ArrayList<>();
        for (MarketStructureSwing candidate : candidates) {
            MarketStructureSwing prior = retained.stream()
                    .filter(s -> s.type() == candidate.type()).reduce((a, b) -> b).orElse(null);
            if (prior != null && candidate.index() - prior.index() < input.minimumSeparationBars()) {
                boolean stronger = candidate.type() == MarketStructureSwingType.HIGH
                        ? candidate.price().compareTo(prior.price()) > 0
                        : candidate.price().compareTo(prior.price()) < 0;
                if (stronger) {
                    retained.remove(prior);
                    all.replaceAll(s -> s == prior ? suppressed(prior, "REPLACED_BY_STRONGER_SAME_TYPE") : s);
                    retained.add(candidate); all.add(candidate);
                } else {
                    all.add(suppressed(candidate, candidate.price().compareTo(prior.price()) == 0
                            ? "EQUAL_RETAINED_EARLIER" : "WITHIN_MINIMUM_SEPARATION"));
                }
            } else { retained.add(candidate); all.add(candidate); }
        }
        retained.sort(Comparator.comparing(MarketStructureSwing::pivotTime)
                .thenComparing(MarketStructureSwing::type));
        return result(input, MarketStructureAvailability.AVAILABLE, retained, all,
                relations(retained), input.findings());
    }

    private MarketStructureSwing swing(MarketStructureInput input, List<MarketStructureCandle> candles,
            int index, MarketStructureSwingType type, java.math.BigDecimal price, java.time.Instant confirmation) {
        int from = index - input.pivotRadius(), to = index + input.pivotRadius();
        return new MarketStructureSwing(type, index, candles.get(index).closeTime(), price,
                confirmation, candles.get(index).sourceId(), candles.get(to).sourceId(),
                candles.get(from).openTime(), candles.get(to).closeTime(),
                candles.subList(from, to + 1).stream().map(MarketStructureCandle::sourceId).toList(),
                candles.subList(from, to + 1).stream().map(c -> new MarketStructureSourceReference(
                        c.sourceId(), c.sourceOccurredAt(), c.fetchedAt())).toList(), false, "");
    }

    private boolean intersectsGap(MarketStructureInput input, List<MarketStructureCandle> candles, int index, int radius) {
        var from = candles.get(index - radius).openTime();
        var to = candles.get(index + radius).closeTime();
        return input.gaps().stream().anyMatch(gap -> gap.from().isBefore(to) && gap.to().isAfter(from));
    }

    private MarketStructureSwing suppressed(MarketStructureSwing value, String reason) {
        return new MarketStructureSwing(value.type(), value.index(), value.pivotTime(),
                value.price(), value.confirmationTime(), value.pivotSourceId(), value.confirmationSourceId(),
                value.evidenceFrom(), value.evidenceTo(), value.evidenceSourceIds(), value.evidenceSources(), true, reason);
    }

    private boolean eligibleWindow(List<MarketStructureCandle> candles, int index, int radius,
            java.time.Instant cutoff) {
        for (MarketStructureCandle candle : candles.subList(index - radius, index + radius + 1)) {
            if (!candle.closed() || candle.synthetic() || candle.closeTime().isAfter(cutoff)) return false;
        }
        return true;
    }

    private boolean sameEvidence(MarketStructureCandle left, MarketStructureCandle right) {
        return left.closeTime().equals(right.closeTime())
                && left.high().compareTo(right.high()) == 0
                && left.low().compareTo(right.low()) == 0;
    }

    private MarketStructureAvailability availability(MarketStructureEvidenceStatus status) {
        return switch (status) {
            case COMPLETE -> MarketStructureAvailability.AVAILABLE;
            case STALE -> MarketStructureAvailability.STALE;
            case INSUFFICIENT -> MarketStructureAvailability.INSUFFICIENT;
            case UNAVAILABLE -> MarketStructureAvailability.UNAVAILABLE;
            case INVALID -> MarketStructureAvailability.INVALID;
        };
    }

    private MarketStructureResult result(MarketStructureInput input, MarketStructureAvailability availability,
            List<MarketStructureSwing> retained, List<MarketStructureSwing> all,
            List<MarketStructureRelationEvidence> relations, List<String> findings) {
        StringBuilder canonical = new StringBuilder("MARKET_STRUCTURE_V2|")
                .append(input.marketId()).append('|').append(input.provider()).append('|')
                .append(input.symbol()).append('|').append(input.interval()).append('|')
                .append(input.cutOffAt()).append('|').append(input.algorithmId()).append('|')
                .append(input.ruleVersion()).append('|').append(input.policyId()).append('|')
                .append(input.policyVersion()).append('|').append(input.parameterFingerprint()).append('|')
                .append(input.inputFingerprint()).append('|').append(availability);
        findings.stream().sorted().forEach(finding -> canonical.append("|finding=").append(finding));
        retained.forEach(s -> canonical.append('|').append(s.type()).append('|').append(s.index())
                .append('|').append(s.pivotTime()).append('|').append(s.price()).append('|')
                .append(s.confirmationTime()).append('|').append(s.pivotSourceId()).append('|')
                .append(s.confirmationSourceId()));
        all.stream().filter(MarketStructureSwing::suppressed).forEach(s -> canonical.append("|suppressed=")
                .append(s.type()).append('|').append(s.index()).append('|').append(s.suppressionReason()));
        relations.forEach(relation -> canonical.append("|relation=").append(relation.swingType()).append('|')
                .append(relation.relation()).append('|').append(relation.previous().pivotSourceId()).append('|')
                .append(relation.latest().pivotSourceId()));
        return new MarketStructureResult(input.marketId(), input.provider(), input.symbol(), input.interval(),
                input.cutOffAt(), input.algorithmId(), input.ruleVersion(), input.policyId(),
                input.policyVersion(), input.parameterFingerprint(), input.inputFingerprint(), availability,
                findings, retained, all, relations, sha256(canonical.toString()));
    }

    private List<MarketStructureRelationEvidence> relations(List<MarketStructureSwing> retained) {
        List<MarketStructureRelationEvidence> result = new ArrayList<>();
        for (MarketStructureSwingType type : MarketStructureSwingType.values()) {
            List<MarketStructureSwing> sameType = retained.stream()
                    .filter(value -> value.type() == type)
                    .sorted(Comparator.comparing(MarketStructureSwing::pivotTime))
                    .toList();
            for (int i = 1; i < sameType.size(); i++) {
                MarketStructureSwing previous = sameType.get(i - 1);
                MarketStructureSwing latest = sameType.get(i);
                int comparison = latest.price().compareTo(previous.price());
                MarketStructureRelation relation = switch (type) {
                    case HIGH -> comparison > 0 ? MarketStructureRelation.HH
                            : comparison < 0 ? MarketStructureRelation.LH : MarketStructureRelation.EQ_HIGH;
                    case LOW -> comparison > 0 ? MarketStructureRelation.HL
                            : comparison < 0 ? MarketStructureRelation.LL : MarketStructureRelation.EQ_LOW;
                };
                result.add(new MarketStructureRelationEvidence(type, relation, previous, latest));
            }
        }
        return result.stream().sorted(Comparator.comparing(value -> value.latest().pivotTime())).toList();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
