package com.hope.trading.market_intelligence.domain.trendcontext;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactContent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable typed result of the Trend Context capability. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record TrendContextCapabilityContent(
        TrendContextAssessment assessment,
        String operationalStatus,
        List<String> diagnostics,
        Map<TrendContextRole, TrendContextSourceReference> sourceReferences,
        Instant assessmentAt,
        Instant cutOffAt,
        String inputFingerprint,
        String assessmentFingerprint
) implements ArtifactContent {
    public TrendContextCapabilityContent {
        operationalStatus = required(operationalStatus, "operationalStatus");
        diagnostics = List.copyOf(diagnostics);
        sourceReferences = Map.copyOf(sourceReferences);
        Objects.requireNonNull(assessmentAt, "assessmentAt is required");
        Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        if (assessment == null) {
            if (inputFingerprint != null || assessmentFingerprint != null) {
                throw new IllegalArgumentException(
                        "Fingerprints require a Trend Context assessment");
            }
        } else {
            if (!assessment.inputFingerprint().equals(inputFingerprint)
                    || !assessment.assessmentFingerprint().equals(assessmentFingerprint)) {
                throw new IllegalArgumentException("Assessment fingerprints do not match content");
            }
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
