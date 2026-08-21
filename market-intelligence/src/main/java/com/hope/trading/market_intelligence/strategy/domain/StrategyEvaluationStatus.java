package com.hope.trading.market_intelligence.strategy.domain;

/**
 * Authoritative outcome of one deterministic strategy evaluation.
 *
 * <ul>
 *   <li>MATCH — conditions satisfied (direction must be present).</li>
 *   <li>NO_MATCH — evaluated normally, conditions not satisfied.</li>
 *   <li>NOT_EVALUABLE — required semantic context unavailable/invalid.</li>
 *   <li>FAILED — unexpected evaluator/application failure.</li>
 * </ul>
 */
public enum StrategyEvaluationStatus {
    MATCH(true),
    NO_MATCH(false),
    NOT_EVALUABLE(false),
    FAILED(false);

    private final boolean requiresDirection;

    StrategyEvaluationStatus(boolean requiresDirection) {
        this.requiresDirection = requiresDirection;
    }

    public boolean requiresDirection() {
        return requiresDirection;
    }
}
