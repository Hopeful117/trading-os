package com.hope.trading.market_intelligence.domain.replay;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SignalReplayRequest(
        UUID marketId,
        String product,
        String instrument,
        String provider,
        StrategyApplicability.Timeframe timeframe,
        Instant evaluationCutoff,
        StrategyId strategyId,
        int strategyVersion,
        EvidenceAcquisitionPolicy evidencePolicy,
        ReplayEvidence evidence,
        ReplayCostAssumptions costAssumptions
) {
    public SignalReplayRequest {
        Objects.requireNonNull(marketId, "market ID is required");
        requireText(product, "product");
        requireText(instrument, "instrument");
        requireText(provider, "provider");
        Objects.requireNonNull(timeframe, "timeframe is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(strategyId, "strategy ID is required");
        if (strategyVersion < 1) throw new IllegalArgumentException("strategy version starts at 1");
        if (evidencePolicy != EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            throw new IllegalArgumentException("signal replay requires STRICT_OBSERVED evidence");
        }
        Objects.requireNonNull(evidence, "evidence is required");
        Objects.requireNonNull(costAssumptions, "cost assumptions are required");
        if (!provider.equals(evidence.provider())) {
            throw new IllegalArgumentException("request provider must match evidence provider");
        }
        if (evidence.latestKnownAt() != null && evidence.latestKnownAt().isAfter(evaluationCutoff)) {
            throw new IllegalArgumentException("evidence known-at is after evaluation cutoff");
        }
        if (evidence.samples().stream().anyMatch(sample -> !sample.admissibleAt(evaluationCutoff))) {
            throw new IllegalArgumentException("evidence contains an inadmissible sample");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
