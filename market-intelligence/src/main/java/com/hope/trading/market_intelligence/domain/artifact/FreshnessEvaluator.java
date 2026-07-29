package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;

import java.time.Duration;
import java.time.Instant;

public final class FreshnessEvaluator {
    public FreshnessAssessment assess(
            ArtifactFreshness freshness,
            FreshnessPolicy policy,
            AnalysisExecutionMode mode,
            Instant now
    ) {
        if (freshness == null || freshness.producedAt() == null
                || freshness.sourceVersion() == null
                || freshness.sourceVersion().isBlank()) {
            return assessment(
                    FreshnessStatus.UNKNOWN, policy.allowUnknown(), policy.allowUnknown(),
                    "Freshness metadata is incomplete", now
            );
        }
        if (freshness.invalidatedAt() != null) {
            return assessment(
                    FreshnessStatus.INVALIDATED, false, false,
                    "Artifact was explicitly invalidated", now
            );
        }
        if (freshness.validUntil() != null && !now.isBefore(freshness.validUntil())) {
            return assessment(
                    FreshnessStatus.EXPIRED, false, false,
                    "Artifact business validity has expired", now
            );
        }

        Duration age = Duration.between(freshness.producedAt(), now);
        if (age.isNegative() || age.compareTo(policy.maximumAge()) <= 0) {
            return assessment(
                    FreshnessStatus.FRESH, true, false,
                    "Artifact satisfies freshness policy", now
            );
        }
        Duration staleAge = age.minus(policy.maximumAge());
        if (staleAge.compareTo(policy.staleTolerance()) <= 0) {
            boolean reusable = policy.allowsStale(mode);
            return assessment(
                    FreshnessStatus.STALE,
                    reusable,
                    reusable,
                    reusable
                            ? "Stale artifact accepted by execution mode policy"
                            : "Stale artifact rejected by freshness policy",
                    now
            );
        }
        return assessment(
                FreshnessStatus.EXPIRED, false, false,
                "Artifact exceeds stale tolerance", now
        );
    }

    private FreshnessAssessment assessment(
            FreshnessStatus status,
            boolean reusable,
            boolean warning,
            String reason,
            Instant now
    ) {
        return new FreshnessAssessment(status, reusable, warning, reason, now);
    }
}
