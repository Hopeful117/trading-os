package com.hope.trading.market_intelligence.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;

/**
 * Transient deterministic result of evaluating one exact strategy version
 * against one semantic context. {@link StrategyEvaluationStatus} is the single
 * source of truth; a concrete direction exists if and only if the status is
 * MATCH.
 */
public final class StrategyEvaluation {

    private final StrategyId strategyId;
    private final int strategyVersion;
    private final UUID marketId;
    private final Instant evaluatedAt;
    private final StrategyEvaluationStatus status;
    private final MatchedDirection direction;
    private final List<ConditionResult> conditionResults;
    private final BigDecimal confidence;
    private final String explanation;
    private final Set<RequiredSemanticInput> consumedInputs;
    private final String contextDigest;

    private StrategyEvaluation(
            StrategyId strategyId,
            int strategyVersion,
            UUID marketId,
            Instant evaluatedAt,
            StrategyEvaluationStatus status,
            MatchedDirection direction,
            List<ConditionResult> conditionResults,
            BigDecimal confidence,
            String explanation,
            Set<RequiredSemanticInput> consumedInputs,
            String contextDigest
    ) {
        Objects.requireNonNull(strategyId, "strategyId is required");
        if (strategyVersion < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        this.strategyId = strategyId;
        this.strategyVersion = strategyVersion;
        this.marketId = marketId;
        this.evaluatedAt = evaluatedAt;
        this.status = Objects.requireNonNull(status, "status is required");
        this.direction = direction;
        this.conditionResults = conditionResults == null ? List.of() : List.copyOf(conditionResults);
        this.confidence = confidence;
        this.explanation = explanation == null || explanation.isBlank() ? null : explanation.trim();
        this.consumedInputs = consumedInputs == null ? Set.of() : Set.copyOf(consumedInputs);
        this.contextDigest = Objects.requireNonNull(contextDigest, "contextDigest is required");

        if (status.requiresDirection() != (direction != null)) {
            throw new IllegalArgumentException(
                    "direction must be present if and only if status is MATCH (status="
                            + status + ", direction=" + direction + ")");
        }
        if (direction != null && confidence == null) {
            throw new IllegalArgumentException("MATCH evaluations require confidence");
        }
    }

    public static StrategyEvaluation match(
            StrategyDefinition definition,
            StrategyEvaluationContext context,
            MatchedDirection direction,
            List<ConditionResult> conditionResults,
            BigDecimal confidence,
            String explanation,
            Set<RequiredSemanticInput> consumedInputs
    ) {
        return build(definition, context, StrategyEvaluationStatus.MATCH, direction,
                conditionResults, confidence, explanation, consumedInputs);
    }

    public static StrategyEvaluation noMatch(
            StrategyDefinition definition,
            StrategyEvaluationContext context,
            List<ConditionResult> conditionResults,
            String explanation,
            Set<RequiredSemanticInput> consumedInputs
    ) {
        return build(definition, context, StrategyEvaluationStatus.NO_MATCH, null,
                conditionResults, null, explanation, consumedInputs);
    }

    public static StrategyEvaluation notEvaluable(
            StrategyDefinition definition,
            StrategyEvaluationContext context,
            String explanation
    ) {
        return build(definition, context, StrategyEvaluationStatus.NOT_EVALUABLE, null,
                List.of(), null, explanation, Set.of());
    }

    public static StrategyEvaluation failed(
            StrategyDefinition definition,
            StrategyEvaluationContext context,
            String explanation
    ) {
        return build(definition, context, StrategyEvaluationStatus.FAILED, null,
                List.of(), null, explanation, Set.of());
    }

    static StrategyEvaluation build(
            StrategyDefinition definition,
            StrategyEvaluationContext context,
            StrategyEvaluationStatus status,
            MatchedDirection direction,
            List<ConditionResult> conditionResults,
            BigDecimal confidence,
            String explanation,
            Set<RequiredSemanticInput> consumedInputs
    ) {
        return new StrategyEvaluation(
                definition.strategyId(), definition.version(), context.marketId(),
                context.evaluatedAt(), status, direction, conditionResults, confidence,
                explanation, consumedInputs, context.digest());
    }

    /** Derived convenience; {@link #status()} remains authoritative. */
    public boolean isMatch() {
        return status == StrategyEvaluationStatus.MATCH;
    }

    public StrategyId strategyId() { return strategyId; }

    public int strategyVersion() { return strategyVersion; }

    public UUID marketId() { return marketId; }

    public Instant evaluatedAt() { return evaluatedAt; }

    public StrategyEvaluationStatus status() { return status; }

    public Optional<MatchedDirection> direction() { return Optional.ofNullable(direction); }

    public List<ConditionResult> conditionResults() { return conditionResults; }

    public Optional<BigDecimal> confidence() { return Optional.ofNullable(confidence); }

    public String explanation() { return explanation; }

    public Set<RequiredSemanticInput> consumedInputs() { return consumedInputs; }

    public String contextDigest() { return contextDigest; }

    @Override
    public boolean equals(Object other) {
        return other instanceof StrategyEvaluation evaluation
                && hashCodeFields().equals(evaluation.hashCodeFields());
    }

    @Override
    public int hashCode() {
        return hashCodeFields().hashCode();
    }

    private List<Object> hashCodeFields() {
        return List.of(strategyId, strategyVersion, marketId, evaluatedAt, status, direction,
                conditionResults, confidence, explanation, consumedInputs, contextDigest);
    }
}
