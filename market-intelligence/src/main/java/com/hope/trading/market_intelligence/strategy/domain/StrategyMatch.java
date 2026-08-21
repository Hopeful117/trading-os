package com.hope.trading.market_intelligence.strategy.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable persisted fact (ADR-034): a specific Strategy version satisfied its
 * deterministic conditions for a specific market and context at a specific
 * time. Created only from a {@link StrategyEvaluationStatus#MATCH} evaluation.
 *
 * <p>The match is append-only provenance: it never changes because the strategy
 * definition evolved, the strategy was retired, new market data arrived or any
 * downstream concern (opportunity, plan, risk) changed.</p>
 *
 * <p>{@code analysisExecutionId} and {@code observationId} are current LIVE
 * evidence provenance (Story 0011), not eternal conceptual requirements of the
 * domain; Story 0013 may generalize persisted provenance additively for
 * Backtest sources. The domain depends only on identifiers, never on pipeline,
 * scanner, gateway or market-data types.</p>
 *
 * <p>Time semantics: {@code matchedAt} is the semantic evaluation time taken
 * verbatim from {@link StrategyEvaluation#evaluatedAt()}; {@code createdAt} is
 * storage time recorded by the persistence path. Note that current shadow
 * evaluation receives the supplied evaluation clock rather than the OHLC event
 * time; this nuance is intentional in Story 0011.</p>
 */
public final class StrategyMatch {

    private final UUID matchId;
    private final StrategyId strategyId;
    private final int strategyVersion;
    private final UUID marketId;
    private final UUID analysisExecutionId;
    private final UUID observationId;
    private final MatchedDirection direction;
    private final String contextDigest;
    private final List<ConditionResult> conditionResults;
    private final Instant matchedAt;
    private final Instant createdAt;

    private StrategyMatch(
            UUID matchId,
            StrategyId strategyId,
            int strategyVersion,
            UUID marketId,
            UUID analysisExecutionId,
            UUID observationId,
            MatchedDirection direction,
            String contextDigest,
            List<ConditionResult> conditionResults,
            Instant matchedAt,
            Instant createdAt
    ) {
        Objects.requireNonNull(matchId, "matchId is required");
        Objects.requireNonNull(strategyId, "strategyId is required");
        if (strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(analysisExecutionId, "analysisExecutionId is required");
        Objects.requireNonNull(observationId, "observationId is required");
        this.direction = Objects.requireNonNull(direction, "MATCH requires a direction");
        this.contextDigest = requireText(contextDigest, "contextDigest");
        this.conditionResults = List.copyOf(
                Objects.requireNonNull(conditionResults, "conditionResults is required"));
        this.matchedAt = Objects.requireNonNull(matchedAt, "matchedAt is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.matchId = matchId;
        this.strategyId = strategyId;
        this.strategyVersion = strategyVersion;
        this.marketId = marketId;
        this.analysisExecutionId = analysisExecutionId;
        this.observationId = observationId;
    }

    /**
     * Creates the immutable fact from a successful MATCH evaluation. Any
     * non-MATCH evaluation is rejected: only MATCH may produce a
     * StrategyMatch.
     */
    public static StrategyMatch fromEvaluation(
            StrategyEvaluation evaluation,
            UUID analysisExecutionId,
            UUID observationId,
            UUID matchId,
            Instant createdAt
    ) {
        Objects.requireNonNull(evaluation, "evaluation is required");
        if (!evaluation.isMatch()) {
            throw new IllegalArgumentException(
                    "only a MATCH evaluation may create a StrategyMatch (status="
                            + evaluation.status() + ")");
        }
        return new StrategyMatch(
                matchId,
                evaluation.strategyId(),
                evaluation.strategyVersion(),
                evaluation.marketId(),
                analysisExecutionId,
                observationId,
                evaluation.direction().orElseThrow(),
                evaluation.contextDigest(),
                evaluation.conditionResults(),
                evaluation.evaluatedAt(),
                createdAt);
    }

    /**
     * Creates the fact from already-extracted MATCH values (pending recording
     * intent path). Same invariants as {@link #fromEvaluation}.
     */
    public static StrategyMatch fromEvaluationFields(
            UUID strategyId,
            int strategyVersion,
            UUID marketId,
            UUID analysisExecutionId,
            UUID observationId,
            MatchedDirection direction,
            String contextDigest,
            List<ConditionResult> conditionResults,
            Instant matchedAt,
            UUID matchId,
            Instant createdAt
    ) {
        return new StrategyMatch(matchId, new StrategyId(strategyId), strategyVersion,
                marketId, analysisExecutionId, observationId, direction, contextDigest,
                conditionResults, matchedAt, createdAt);
    }

    /** Rehydrates a persisted match. Persistence adapters only. */
    public static StrategyMatch rehydrate(
            UUID matchId,
            StrategyId strategyId,
            int strategyVersion,
            UUID marketId,
            UUID analysisExecutionId,
            UUID observationId,
            MatchedDirection direction,
            String contextDigest,
            List<ConditionResult> conditionResults,
            Instant matchedAt,
            Instant createdAt
    ) {
        return new StrategyMatch(matchId, strategyId, strategyVersion, marketId,
                analysisExecutionId, observationId, direction, contextDigest,
                conditionResults, matchedAt, createdAt);
    }

    public StrategyMatchIdentity identity() {
        return new StrategyMatchIdentity(strategyId.value(), strategyVersion,
                marketId, analysisExecutionId, contextDigest);
    }

    public UUID matchId() { return matchId; }

    public StrategyId strategyId() { return strategyId; }

    public int strategyVersion() { return strategyVersion; }

    public UUID marketId() { return marketId; }

    public UUID analysisExecutionId() { return analysisExecutionId; }

    public UUID observationId() { return observationId; }

    public MatchedDirection direction() { return direction; }

    public String contextDigest() { return contextDigest; }

    public List<ConditionResult> conditionResults() { return conditionResults; }

    public Instant matchedAt() { return matchedAt; }

    public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return other instanceof StrategyMatch match && matchId.equals(match.matchId);
    }

    @Override
    public int hashCode() {
        return matchId.hashCode();
    }

    @Override
    public String toString() {
        return "StrategyMatch{" + "matchId=" + matchId + ", strategy=" + strategyId
                + "v" + strategyVersion + ", market=" + marketId + ", direction="
                + direction + ", matchedAt=" + matchedAt + '}';
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
