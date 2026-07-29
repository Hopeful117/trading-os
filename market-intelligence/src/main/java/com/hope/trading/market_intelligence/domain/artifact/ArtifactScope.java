package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;

import java.util.Objects;
import java.util.UUID;

public record ArtifactScope(
        UUID marketId,
        String instrument,
        String timeframe,
        UUID userId,
        UUID accountId,
        String tenantId,
        AnalysisExecutionMode analysisMode,
        ContextClassification classification
) {
    public ArtifactScope {
        instrument = normalize(instrument);
        timeframe = normalize(timeframe);
        tenantId = normalize(tenantId);
        Objects.requireNonNull(classification, "classification");
        if (classification.ordinal() >= ContextClassification.USER_CONFIDENTIAL.ordinal()
                && userId == null && accountId == null && tenantId == null) {
            throw new IllegalArgumentException(
                    "Confidential artifact scope requires user, account or tenant isolation"
            );
        }
    }

    public static ArtifactScope publicMarket(
            UUID marketId,
            String timeframe,
            AnalysisExecutionMode mode
    ) {
        return new ArtifactScope(
                marketId, null, timeframe, null, null, null, mode,
                ContextClassification.PUBLIC
        );
    }

    public boolean isCompatibleWith(ArtifactScope requested) {
        return equals(requested);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
