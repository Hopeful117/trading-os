package com.hope.trading.risk.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ContextMetadata(
        UUID accountId, long accountVersion, UUID portfolioId, long portfolioVersion,
        long marketVersion, long ruleSetVersion,
        Instant accountCapturedAt, Instant portfolioCapturedAt,
        Instant marketCapturedAt, Instant ruleSetCapturedAt
) {
    public ContextMetadata {
        Objects.requireNonNull(accountId); Objects.requireNonNull(portfolioId);
        Objects.requireNonNull(accountCapturedAt); Objects.requireNonNull(portfolioCapturedAt);
        Objects.requireNonNull(marketCapturedAt); Objects.requireNonNull(ruleSetCapturedAt);
    }
}
