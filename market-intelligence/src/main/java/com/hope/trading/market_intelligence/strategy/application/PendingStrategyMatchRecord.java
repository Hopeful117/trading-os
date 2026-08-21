package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable recording intent created INSIDE the pipeline transaction (T1) and
 * carried through the after-commit callback. It snapshots everything required
 * to persist the fact so that no mutable pipeline state or JPA entity ever
 * crosses the transaction boundary.
 *
 * <p>Created only from a MATCH evaluation; non-MATCH evaluations never
 * produce a pending record.</p>
 */
public record PendingStrategyMatchRecord(
        UUID strategyId,
        int strategyVersion,
        UUID marketId,
        UUID analysisExecutionId,
        UUID observationId,
        MatchedDirection direction,
        String contextDigest,
        List<ConditionResult> conditionResults,
        Instant matchedAt
) {
    public PendingStrategyMatchRecord {
        Objects.requireNonNull(strategyId, "strategyId is required");
        if (strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(analysisExecutionId, "analysisExecutionId is required");
        Objects.requireNonNull(observationId, "observationId is required");
        direction = java.util.Objects.requireNonNull(
                direction, "MATCH requires a direction");
        Objects.requireNonNull(contextDigest, "contextDigest is required");
        conditionResults = List.copyOf(
                Objects.requireNonNull(conditionResults, "conditionResults is required"));
        matchedAt = Objects.requireNonNull(matchedAt, "matchedAt is required");
    }

    /** Snapshots a successful evaluation into an immutable intent. */
    public static PendingStrategyMatchRecord fromEvaluation(
            StrategyEvaluation evaluation,
            UUID analysisExecutionId,
            UUID observationId
    ) {
        Objects.requireNonNull(evaluation, "evaluation is required");
        if (!evaluation.isMatch()) {
            throw new IllegalArgumentException(
                    "only a MATCH evaluation may create a pending StrategyMatch record "
                            + "(status=" + evaluation.status() + ")");
        }
        return new PendingStrategyMatchRecord(
                evaluation.strategyId().value(),
                evaluation.strategyVersion(),
                evaluation.marketId(),
                analysisExecutionId,
                observationId,
                evaluation.direction().orElseThrow(),
                evaluation.contextDigest(),
                evaluation.conditionResults(),
                evaluation.evaluatedAt());
    }

    /** Logical live identity used for idempotent persistence. */
    public StrategyMatchIdentity identity() {
        return new StrategyMatchIdentity(strategyId, strategyVersion, marketId,
                analysisExecutionId, contextDigest);
    }
}
