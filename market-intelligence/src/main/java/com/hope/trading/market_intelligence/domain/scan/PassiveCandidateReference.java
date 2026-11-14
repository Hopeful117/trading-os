package com.hope.trading.market_intelligence.domain.scan;

import java.util.Objects;
import java.util.UUID;

/**
 * Client-supplied reference to a passive candidate: an exact
 * {@code TradingOpportunity(PASSIVE_SCAN)} version. It is never trusted
 * without server-side revalidation.
 */
public record PassiveCandidateReference(UUID opportunityId, long opportunityVersion) {
    public PassiveCandidateReference {
        Objects.requireNonNull(opportunityId, "opportunityId");
        if (opportunityVersion < 1) {
            throw new IllegalArgumentException("opportunityVersion must be positive");
        }
    }
}
