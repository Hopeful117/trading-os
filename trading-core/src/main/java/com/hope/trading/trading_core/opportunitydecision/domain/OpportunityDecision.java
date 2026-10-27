package com.hope.trading.trading_core.opportunitydecision.domain;

import java.time.Instant;
import java.util.UUID;

public record OpportunityDecision(
        UUID id,
        UUID actorId,
        UUID accountId,
        UUID opportunityId,
        long opportunityVersion,
        UUID thesisId,
        long thesisVersion,
        Decision decision,
        String rationale,
        String idempotencyKey,
        Instant createdAt
) {
    public enum Decision { NO_TRADE }
}
