package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Map;
import java.util.Set;

/**
 * Governance lifecycle of a strategy version (ADR-034).
 *
 * <p>CANDIDATE to VALIDATED and VALIDATED to ENABLED additionally require
 * accepted validation evidence; that invariant is enforced by the aggregate.</p>
 */
public enum StrategyLifecycle {
    DRAFT,
    CANDIDATE,
    VALIDATED,
    ENABLED,
    RETIRED;

    private static final Map<StrategyLifecycle, Set<StrategyLifecycle>> LEGAL_TRANSITIONS = Map.of(
            DRAFT, Set.of(CANDIDATE, RETIRED),
            CANDIDATE, Set.of(VALIDATED, RETIRED),
            VALIDATED, Set.of(ENABLED, RETIRED),
            ENABLED, Set.of(RETIRED),
            RETIRED, Set.of());

    public boolean canTransitionTo(StrategyLifecycle target) {
        return LEGAL_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == RETIRED;
    }
}
