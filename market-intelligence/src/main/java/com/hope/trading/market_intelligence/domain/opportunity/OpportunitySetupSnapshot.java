package com.hope.trading.market_intelligence.domain.opportunity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic market-setup snapshot captured when the opportunity was
 * created (Story 0029). It records what was true at detection time —
 * never live market state — so a trader can understand why the setup
 * deserved attention without preparing a TradePlan, and so the reason a
 * historical opportunity existed stays reproducible.
 *
 * <p>Invariants:</p>
 * <ul>
 *   <li>{@code referencePrice}/{@code referencePriceAt} are the price
 *       observed by the evidence that fed the match and its observation
 *       instant; both are null only when detection context carried no
 *       price (legacy or price-less strategies) — never fetched after
 *       the fact.</li>
 *   <li>{@code description} is evaluator-produced and deterministic:
 *       identical match inputs yield an identical description.</li>
 *   <li>{@code triggers} mirror the matched conditions with their
 *       observed values; at least one trigger is required.</li>
 *   <li>The snapshot is immutable across opportunity version
 *       transitions: status changes copy it verbatim.</li>
 * </ul>
 */
public record OpportunitySetupSnapshot(
        BigDecimal referencePrice,
        Instant referencePriceAt,
        String description,
        List<OpportunityTrigger> triggers,
        Instant detectedAt
) {
    public OpportunitySetupSnapshot {
        if (referencePrice == null ^ referencePriceAt == null) {
            throw new IllegalArgumentException(
                    "referencePrice and referencePriceAt must be provided together");
        }
        description = required(description, "description");
        triggers = List.copyOf(triggers);
        if (triggers.isEmpty()) {
            throw new IllegalArgumentException("At least one trigger is required");
        }
        Objects.requireNonNull(detectedAt, "detectedAt is required");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return result;
    }
}
