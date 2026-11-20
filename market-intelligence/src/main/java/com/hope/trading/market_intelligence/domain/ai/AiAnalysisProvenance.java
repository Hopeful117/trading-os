package com.hope.trading.market_intelligence.domain.ai;

import java.time.Instant;
import java.util.Objects;

public record AiAnalysisProvenance(
        String provider,
        String model,
        String contractVersion,
        Instant invokedAt,
        Instant completedAt,
        boolean fallback,
        String failureReason
) {
    public AiAnalysisProvenance {
        contractVersion = required(contractVersion, "contractVersion");
        Objects.requireNonNull(invokedAt, "invokedAt");
        if (completedAt != null && completedAt.isBefore(invokedAt)) {
            throw new IllegalArgumentException("completedAt cannot precede invokedAt");
        }
        if (failureReason != null && failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason cannot be blank");
        }
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
