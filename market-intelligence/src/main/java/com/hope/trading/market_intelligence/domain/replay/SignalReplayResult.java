package com.hope.trading.market_intelligence.domain.replay;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SignalReplayResult(
        UUID resultId,
        UUID marketId,
        String product,
        String instrument,
        String provider,
        StrategyApplicability.Timeframe timeframe,
        Instant evaluationCutoff,
        StrategyId strategyId,
        int strategyVersion,
        EvidenceAcquisitionPolicy evidencePolicy,
        ReplayEvidenceStatus evidenceStatus,
        List<ReplayEvidenceSample> evidenceSamples,
        String evidenceFingerprint,
        String contextFingerprint,
        StrategyEvaluationStatus evaluationStatus,
        MatchedDirection direction,
        ReplayCostAssumptions costAssumptions,
        List<String> diagnostics,
        String fingerprint,
        Instant createdAt
) {
    public SignalReplayResult {
        Objects.requireNonNull(resultId);
        Objects.requireNonNull(marketId);
        requireText(product, "product");
        requireText(instrument, "instrument");
        requireText(provider, "provider");
        Objects.requireNonNull(timeframe);
        Objects.requireNonNull(evaluationCutoff);
        Objects.requireNonNull(strategyId);
        if (strategyVersion < 1) throw new IllegalArgumentException("strategy version starts at 1");
        if (evidencePolicy != EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            throw new IllegalArgumentException("signal replay result requires STRICT_OBSERVED evidence");
        }
        Objects.requireNonNull(evidenceStatus);
        evidenceSamples = evidenceSamples == null ? List.of() : evidenceSamples.stream()
                .sorted(java.util.Comparator.comparing(value -> value.observationFingerprint()))
                .toList();
        if (evidenceStatus == ReplayEvidenceStatus.COMPLETE && evidenceSamples.isEmpty()) {
            throw new IllegalArgumentException("complete replay result requires evidence samples");
        }
        if (evidenceStatus == ReplayEvidenceStatus.COMPLETE
                && evidenceSamples.stream().anyMatch(value -> !value.admissibleAt(evaluationCutoff))) {
            throw new IllegalArgumentException("complete replay result contains inadmissible evidence");
        }
        Objects.requireNonNull(evaluationStatus);
        if (evidenceStatus != ReplayEvidenceStatus.COMPLETE
                && evaluationStatus != StrategyEvaluationStatus.NOT_EVALUABLE) {
            throw new IllegalArgumentException("inadmissible evidence requires NOT_EVALUABLE status");
        }
        requireText(evidenceFingerprint, "evidence fingerprint");
        requireText(contextFingerprint, "context fingerprint");
        if ((evaluationStatus == StrategyEvaluationStatus.MATCH) != (direction != null)) {
            throw new IllegalArgumentException("direction must exist if and only if evaluation matches");
        }
        Objects.requireNonNull(costAssumptions);
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream().sorted().toList();
        requireText(fingerprint, "fingerprint");
        Objects.requireNonNull(createdAt);
    }

    public static SignalReplayResult from(
            SignalReplayRequest request,
            String contextFingerprint,
            StrategyEvaluationStatus evaluationStatus,
            MatchedDirection direction,
            List<String> diagnostics,
            Instant createdAt
    ) {
        List<String> normalizedDiagnostics = diagnostics == null ? List.of() : diagnostics.stream().sorted().toList();
        List<String> material = new java.util.ArrayList<>(List.of(
                "market=" + request.marketId(),
                "product=" + request.product(),
                "instrument=" + request.instrument(),
                "provider=" + request.provider(),
                "timeframe=" + request.timeframe(),
                "cutoff=" + request.evaluationCutoff(),
                "strategy=" + request.strategyId().value(),
                "strategyVersion=" + request.strategyVersion(),
                "policy=" + request.evidencePolicy(),
                "evidenceStatus=" + request.evidence().status(),
                "latestKnownAt=" + request.evidence().latestKnownAt(),
                "evidenceFingerprint=" + request.evidence().sourceFingerprint(),
                "context=" + contextFingerprint,
                "evaluationStatus=" + evaluationStatus,
                "direction=" + direction,
                "costVersion=" + request.costAssumptions().version(),
                "spread=" + request.costAssumptions().spread().toPlainString(),
                "transactionCost=" + request.costAssumptions().transactionCost().toPlainString()
        ));
        request.evidence().samples().forEach(sample -> material.add("sample=" + sample));
        normalizedDiagnostics.forEach(value -> material.add("diagnostic=" + value));
        String fingerprint = fingerprint(material);
        return new SignalReplayResult(
                UUID.randomUUID(), request.marketId(), request.product(), request.instrument(), request.provider(),
                request.timeframe(), request.evaluationCutoff(), request.strategyId(), request.strategyVersion(),
                request.evidencePolicy(), request.evidence().status(), request.evidence().samples(),
                request.evidence().sourceFingerprint(), contextFingerprint,
                evaluationStatus, direction,
                request.costAssumptions(), normalizedDiagnostics, fingerprint, createdAt);
    }

    public boolean hasSameSemanticContent(SignalReplayResult other) {
        return marketId.equals(other.marketId)
                && product.equals(other.product)
                && instrument.equals(other.instrument)
                && provider.equals(other.provider)
                && timeframe == other.timeframe
                && evaluationCutoff.equals(other.evaluationCutoff)
                && strategyId.equals(other.strategyId)
                && strategyVersion == other.strategyVersion
                && evidencePolicy == other.evidencePolicy
                && evidenceStatus == other.evidenceStatus
                && evidenceSamples.equals(other.evidenceSamples)
                && evidenceFingerprint.equals(other.evidenceFingerprint)
                && contextFingerprint.equals(other.contextFingerprint)
                && evaluationStatus == other.evaluationStatus
                && direction == other.direction
                && costAssumptions.equals(other.costAssumptions)
                && diagnostics.equals(other.diagnostics)
                && fingerprint.equals(other.fingerprint);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String fingerprint(List<String> values) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream output = new java.io.DataOutputStream(bytes);
            for (String value : values) {
                byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            output.flush();
            return java.util.HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot fingerprint replay result", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
