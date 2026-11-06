package com.hope.trading.market_intelligence.domain;

import java.time.Instant;

public record ContextSectionSummary(
        ContextSectionType type,
        ContextSectionStatus status,
        ContextSensitivity sensitivity,
        String source,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        String sourceReference,
        String sourceVersion,
        String normalizationVersion,
        EvidenceAcquisitionPolicy evidencePolicy,
        String evidenceFingerprint,
        Integer observationCount,
        Instant evaluationCutoff,
        Instant capturedAt,
        Instant knownAt,
        String message
) {
    public ContextSectionSummary(
            ContextSectionType type, ContextSectionStatus status, ContextSensitivity sensitivity,
        String source, Instant sourceOccurredAt, String message) {
        this(type, status, sensitivity, source, sourceOccurredAt,
                null, null, null, null, null, null, null, null, message);
    }

    public ContextSectionSummary(
            ContextSectionType type, ContextSectionStatus status, ContextSensitivity sensitivity,
            String source, Instant sourceOccurredAt, Instant fetchedAt, String sourceReference,
            String sourceVersion, String normalizationVersion, EvidenceAcquisitionPolicy evidencePolicy,
            String evidenceFingerprint, Integer observationCount, Instant evaluationCutoff,
            String message) {
        this(type, status, sensitivity, source, sourceOccurredAt, fetchedAt, sourceReference,
                sourceVersion, normalizationVersion, evidencePolicy, evidenceFingerprint,
                observationCount, evaluationCutoff, null, null, message);
    }
}
