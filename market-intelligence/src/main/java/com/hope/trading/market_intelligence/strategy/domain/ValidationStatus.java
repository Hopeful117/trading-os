package com.hope.trading.market_intelligence.strategy.domain;

/**
 * Truth about whether accepted deterministic/empirical strategy-validation
 * evidence exists for a strategy version (ADR-034, boundary formalized by
 * ADR-038).
 *
 * <p>Three concepts are deliberately distinct:</p>
 * <ul>
 *   <li>TECHNICAL CORRECTNESS — the evaluator implements its declared rules;
 *       proven by tests, proofs and parity. Never changes this status.</li>
 *   <li>STRATEGY VALIDATION — accepted empirical/deterministic evidence that
 *       this exact version is fit for use. Only this justifies VALIDATED.
 *       The capability producing such evidence does not exist yet, so normal
 *       strategies legitimately remain UNVALIDATED.</li>
 *   <li>OPERATIONAL ACTIVATION — live authorization; owned exclusively by
 *       {@link StrategyOperationalStatus}. VALIDATED never implies ENABLED.</li>
 * </ul>
 */
public enum ValidationStatus {
    UNVALIDATED,
    VALIDATED
}
