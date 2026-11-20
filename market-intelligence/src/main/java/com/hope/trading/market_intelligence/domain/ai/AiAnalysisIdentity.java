package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;

import java.util.Objects;
import java.util.UUID;

public record AiAnalysisIdentity(
        UUID executionId,
        IdempotencyKey idempotencyKey,
        AiInterpretationMode mode,
        ContextPackId contextPackId,
        String contextPackSchemaVersion,
        ArtifactFingerprint contextPackFingerprint,
        UUID marketId,
        OpportunityVersionReference opportunity,
        MarketThesisReference thesis
) {
    public AiAnalysisIdentity {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(contextPackId, "contextPackId");
        contextPackSchemaVersion = required(contextPackSchemaVersion, "contextPackSchemaVersion");
        Objects.requireNonNull(contextPackFingerprint, "contextPackFingerprint");
        Objects.requireNonNull(marketId, "marketId");
        Objects.requireNonNull(opportunity, "opportunity");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
