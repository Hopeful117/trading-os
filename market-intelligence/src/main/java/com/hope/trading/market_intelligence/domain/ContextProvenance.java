package com.hope.trading.market_intelligence.domain;

import java.time.Instant;

public record ContextProvenance(
        String source,
        Instant sourceOccurredAt,
        Instant fetchedAt,
        String sourceReference,
        String sourceVersion,
        String normalizationVersion,
        EvidenceAcquisitionPolicy evidencePolicy,
        String evidenceFingerprint,
        Integer observationCount,
        Instant evaluationCutoff
) {
    public ContextProvenance(String source, Instant sourceOccurredAt, Instant fetchedAt) {
        this(source, sourceOccurredAt, fetchedAt, null, null, null, null, null, null, null);
    }

    public ContextProvenance(
            String source,
            Instant sourceOccurredAt,
            Instant fetchedAt,
            String sourceReference,
            String sourceVersion,
            String normalizationVersion
    ) {
        this(source, sourceOccurredAt, fetchedAt, sourceReference, sourceVersion,
                normalizationVersion, null, null, null, null);
    }

    public ContextProvenance(
            String source,
            Instant sourceOccurredAt,
            Instant fetchedAt,
            String sourceReference,
            String sourceVersion,
            String normalizationVersion,
            EvidenceAcquisitionPolicy evidencePolicy,
            String evidenceFingerprint,
            Integer observationCount
    ) {
        this(source, sourceOccurredAt, fetchedAt, sourceReference, sourceVersion,
                normalizationVersion, evidencePolicy, evidenceFingerprint, observationCount, null);
    }
}
