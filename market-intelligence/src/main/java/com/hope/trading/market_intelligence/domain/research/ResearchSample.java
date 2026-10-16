package com.hope.trading.market_intelligence.domain.research;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.*;

public record ResearchSample(
        UUID sampleId,
        UUID marketId,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        String featureProjectionVersion,
        FeatureSnapshot featureSnapshot,
        ObservedMarketOutcome outcome,
        ResearchSampleStatus status,
        ResearchExclusionReason exclusionReason,
        String sampleFingerprint
) {
    public ResearchSample {
        Objects.requireNonNull(sampleId, "sample ID is required");
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
        Objects.requireNonNull(featureProjectionVersion, "feature projection version is required");
        Objects.requireNonNull(status, "sample status is required");
        Objects.requireNonNull(sampleFingerprint, "sample fingerprint is required");
        if (status == ResearchSampleStatus.ELIGIBLE && (featureSnapshot == null || outcome == null || exclusionReason != null)) {
            throw new IllegalArgumentException("eligible sample requires both facts and no exclusion");
        }
        if (status == ResearchSampleStatus.EXCLUDED && exclusionReason == null) {
            throw new IllegalArgumentException("excluded sample requires a reason");
        }
    }

    public static ResearchSample create(
            String definitionVersion,
            FeatureSnapshot snapshot,
            ObservedMarketOutcome outcome,
            ResearchSampleStatus status,
            ResearchExclusionReason reason
    ) {
        Objects.requireNonNull(snapshot, "feature snapshot is required");
        UUID marketId = snapshot.marketId();
        Instant cutoff = snapshot.evaluationCutoff();
        OutcomeHorizon horizon = outcome == null ? null : outcome.horizon();
        if (horizon == null) throw new IllegalArgumentException("outcome horizon is required");
        String fingerprint = fingerprint(definitionVersion, snapshot, outcome, status, reason);
        return new ResearchSample(UUID.nameUUIDFromBytes(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                marketId, cutoff, horizon, snapshot.featureProjectionVersion(), snapshot, outcome, status, reason, fingerprint);
    }

    public static ResearchSample excluded(
            String definitionVersion, FeatureSnapshot snapshot, OutcomeHorizon horizon,
            ResearchExclusionReason reason
    ) {
        Objects.requireNonNull(snapshot, "feature snapshot is required");
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(
                "definition=" + definitionVersion,
                "market=" + snapshot.marketId(),
                "cutoff=" + snapshot.evaluationCutoff(),
                "horizon=" + horizon.value(),
                "feature=" + snapshot.fingerprint(),
                "reason=" + reason)).value();
        return new ResearchSample(UUID.nameUUIDFromBytes(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                snapshot.marketId(), snapshot.evaluationCutoff(), horizon, snapshot.featureProjectionVersion(),
                snapshot, null, ResearchSampleStatus.EXCLUDED, reason, fingerprint);
    }

    private static String fingerprint(String definitionVersion, FeatureSnapshot snapshot,
                                      ObservedMarketOutcome outcome, ResearchSampleStatus status,
                                      ResearchExclusionReason reason) {
        return ArtifactFingerprint.ofInputs(List.of(
                "definition=" + definitionVersion,
                "market=" + snapshot.marketId(),
                "cutoff=" + snapshot.evaluationCutoff(),
                "horizon=" + outcome.horizon().value(),
                "projection=" + snapshot.featureProjectionVersion(),
                "feature=" + snapshot.fingerprint(),
                "outcome=" + outcome.outcomeFingerprint(),
                "status=" + status,
                "reason=" + Objects.toString(reason, "null"))).value();
    }
}
