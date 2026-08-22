package com.hope.trading.market_intelligence.application.opportunity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Application trigger that makes the existing opportunity expiration
 * lifecycle effective in production (Story 0018).
 *
 * <p>Deliberately rule-free: at each technical tick it simply asks the
 * {@link OpportunityRegistry} to expire whatever is due according to the
 * configured {@code OpportunityExpirationPolicy} at the current injected
 * {@link Clock} instant. This component knows nothing about validity
 * windows, statuses, timeframes or strategies.</p>
 *
 * <p>The scheduling interval is a TECHNICAL configuration only. It has no
 * business meaning: opportunity lifetime is defined exclusively by
 * {@code TradingOpportunity.validUntil} (inherited from the Observation),
 * evaluated by {@link ValidityWindowExpirationPolicy}. Because expiration is
 * a time predicate over current latest opportunities, missed ticks are
 * naturally caught up on the next tick (restart-safe), and repeated ticks
 * are idempotent through the existing lifecycle.</p>
 */
@Component
public class OpportunityExpirationDriver {

    private static final Logger log = LoggerFactory.getLogger(OpportunityExpirationDriver.class);

    private final OpportunityRegistry registry;
    private final OpportunityExpirationPolicy policy;
    private final Clock clock;

    public OpportunityExpirationDriver(
            OpportunityRegistry registry,
            OpportunityExpirationPolicy policy,
            Clock clock) {
        this.registry = registry;
        this.policy = policy;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${intelligence.opportunity.expiration-check-interval-millis:30000}",
            initialDelayString =
                    "${intelligence.opportunity.expiration-check-initial-delay-millis:10000}")
    public void expireDueOpportunities() {
        int expired = registry.expireDue(policy, clock.instant()).size();
        if (expired > 0) {
            log.info("Opportunity expiration tick expired {} opportunity(ies)", expired);
        }
    }
}
