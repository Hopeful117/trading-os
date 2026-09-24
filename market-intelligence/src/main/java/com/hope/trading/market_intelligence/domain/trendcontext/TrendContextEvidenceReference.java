package com.hope.trading.market_intelligence.domain.trendcontext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TrendContextEvidenceReference(
        TrendContextRole role, String interval, String ruleId, String ruleVersion,
        String profileVersion, String inputFingerprint, Instant cutOffAt,
        List<String> sourceIds, Instant from, Instant to, String key) {
    public TrendContextEvidenceReference {
        Objects.requireNonNull(role); Objects.requireNonNull(interval);
        Objects.requireNonNull(ruleId); Objects.requireNonNull(ruleVersion);
        Objects.requireNonNull(profileVersion); Objects.requireNonNull(inputFingerprint);
        Objects.requireNonNull(cutOffAt); sourceIds = List.copyOf(sourceIds == null ? List.of() : sourceIds);
        key = Objects.requireNonNull(key);
    }
}
