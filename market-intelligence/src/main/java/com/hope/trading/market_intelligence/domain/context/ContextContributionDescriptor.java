package com.hope.trading.market_intelligence.domain.context;

import com.hope.trading.market_intelligence.domain.ContextSectionType;

import java.time.Duration;
import java.util.Set;

public record ContextContributionDescriptor(
        Set<ContextSectionType> providedSections,
        ContextClassification classification,
        Duration freshness,
        Set<ContextConsumer> allowedConsumers
) {
    public ContextContributionDescriptor {
        providedSections = Set.copyOf(providedSections);
        allowedConsumers = Set.copyOf(allowedConsumers);
        if (providedSections.isEmpty() || classification == null
                || freshness == null || freshness.isNegative()
                || allowedConsumers.isEmpty()) {
            throw new IllegalArgumentException("Invalid context contribution descriptor");
        }
    }

    public boolean permits(ContextConsumer consumer) {
        return classification != ContextClassification.RESTRICTED
                && allowedConsumers.contains(consumer);
    }

    public static ContextContributionDescriptor publicMarket(ContextSectionType section) {
        return new ContextContributionDescriptor(
                Set.of(section),
                ContextClassification.PUBLIC,
                Duration.ofSeconds(30),
                Set.of(
                        ContextConsumer.DETERMINISTIC_CAPABILITY,
                        ContextConsumer.AI_CAPABILITY,
                        ContextConsumer.CONSOLIDATOR
                )
        );
    }
}
