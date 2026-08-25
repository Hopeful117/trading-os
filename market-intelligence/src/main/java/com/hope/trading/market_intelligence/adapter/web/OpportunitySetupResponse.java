package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunitySetupSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Trader-facing projection of the deterministic setup snapshot
 * (Story 0029). Null on the enclosing response for opportunities created
 * before Story 0029.
 */
public record OpportunitySetupResponse(
        BigDecimal referencePrice,
        Instant referencePriceAt,
        String description,
        List<Trigger> triggers,
        Instant detectedAt
) {
    public record Trigger(String condition, String observedValue) {}

    public static Optional<OpportunitySetupResponse> from(
            OpportunitySetupSnapshot snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(new OpportunitySetupResponse(
                snapshot.referencePrice(),
                snapshot.referencePriceAt(),
                snapshot.description(),
                snapshot.triggers().stream()
                        .map(trigger -> new Trigger(
                                trigger.condition(), trigger.observedValue()))
                        .toList(),
                snapshot.detectedAt()));
    }
}
