package com.hope.trading.market_intelligence.domain.tradeability;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TradeabilityCapabilityContent(
        TradeabilityAssessment assessment,
        String operationalStatus,
        List<String> diagnostics,
        Instant validUntil
) implements ArtifactContent {
    public TradeabilityCapabilityContent {
        Objects.requireNonNull(assessment, "assessment");
        operationalStatus = required(operationalStatus, "operationalStatus");
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        Objects.requireNonNull(validUntil, "validUntil");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
