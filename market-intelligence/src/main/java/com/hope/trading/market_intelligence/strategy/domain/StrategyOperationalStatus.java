package com.hope.trading.market_intelligence.strategy.domain;

import java.util.Map;
import java.util.Set;

/**
 * Operational activation status of a strategy version (ADR-036).
 *
 * <p>Answers exactly one question: is this strategy currently authorized to
 * participate in live evaluation? It deliberately carries no maturity
 * semantics — validation truth lives in {@link ValidationStatus} (ADR-034).</p>
 *
 * <ul>
 *   <li>{@code DISABLED}: not authorized for live evaluation.</li>
 *   <li>{@code ENABLED}: fully governed authorization; transitioning to this
 *       state requires {@code ValidationStatus.VALIDATED} plus accepted
 *       evidence (enforced by the aggregate).</li>
 *   <li>{@code BOOTSTRAP_CONTROLLED_RUN}: temporary, human-approved controlled
 *       run of the unvalidated bootstrap migration vehicle (ADR-034 exception,
 *       made explicit by ADR-036). Subject to shadow parity monitoring.</li>
 *   <li>{@code RETIRED}: permanently withdrawn; terminal.</li>
 * </ul>
 */
public enum StrategyOperationalStatus {
    DISABLED,
    ENABLED,
    BOOTSTRAP_CONTROLLED_RUN,
    RETIRED;

    private static final Map<StrategyOperationalStatus, Set<StrategyOperationalStatus>>
            LEGAL_TRANSITIONS = Map.of(
            DISABLED, Set.of(ENABLED, BOOTSTRAP_CONTROLLED_RUN, RETIRED),
            ENABLED, Set.of(DISABLED, RETIRED),
            BOOTSTRAP_CONTROLLED_RUN, Set.of(RETIRED),
            RETIRED, Set.of());

    public boolean canTransitionTo(StrategyOperationalStatus target) {
        return LEGAL_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == RETIRED;
    }
}
