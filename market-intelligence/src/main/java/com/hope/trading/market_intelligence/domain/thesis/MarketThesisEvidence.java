package com.hope.trading.market_intelligence.domain.thesis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketThesisEvidence(
        UUID evidenceId,
        MarketThesisEvidenceRole role,
        MarketThesisAvailability availability,
        MarketThesisContradictionSeverity severity,
        String source,
        String reference,
        String timeframe,
        String explanation,
        Instant observedAt,
        Instant validUntil,
        String provider,
        String instrument,
        String sourceFingerprint,
        String ruleVersion,
        Instant sourceCutoff,
        boolean required
) {
    public MarketThesisEvidence(
            UUID evidenceId, MarketThesisEvidenceRole role,
            MarketThesisAvailability availability,
            MarketThesisContradictionSeverity severity,
            String source, String reference, String timeframe,
            String explanation, Instant observedAt, Instant validUntil) {
        this(evidenceId, role, availability, severity, source, reference, timeframe,
                explanation, observedAt, validUntil, null, null, null, null, null, false);
    }

    public MarketThesisEvidence(
            UUID evidenceId, MarketThesisEvidenceRole role,
            MarketThesisAvailability availability,
            MarketThesisContradictionSeverity severity,
            String source, String reference, String timeframe,
            String explanation, Instant observedAt, Instant validUntil,
            String provider, String instrument, String sourceFingerprint,
            String ruleVersion) {
        this(evidenceId, role, availability, severity, source, reference, timeframe,
                explanation, observedAt, validUntil, provider, instrument,
                sourceFingerprint, ruleVersion, null, false);
    }

    public MarketThesisEvidence(
            UUID evidenceId, MarketThesisEvidenceRole role,
            MarketThesisAvailability availability,
            MarketThesisContradictionSeverity severity,
            String source, String reference, String timeframe,
            String explanation, Instant observedAt, Instant validUntil,
            String provider, String instrument, String sourceFingerprint,
            String ruleVersion, Instant sourceCutoff) {
        this(evidenceId, role, availability, severity, source, reference, timeframe,
                explanation, observedAt, validUntil, provider, instrument,
                sourceFingerprint, ruleVersion, sourceCutoff, false);
    }

    public MarketThesisEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(availability, "availability");
        source = required(source, "source");
        reference = required(reference, "reference");
        explanation = required(explanation, "explanation");
        Objects.requireNonNull(observedAt, "observedAt");
        if (role != MarketThesisEvidenceRole.CONTRADICTING && severity != null) {
            throw new IllegalArgumentException("Only contradicting evidence has severity");
        }
        if (role == MarketThesisEvidenceRole.SUPPORTING
                && availability != MarketThesisAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Unavailable evidence cannot support a thesis");
        }
        if (role == MarketThesisEvidenceRole.MISSING
                && availability == MarketThesisAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Available evidence cannot be missing");
        }
        if (role == MarketThesisEvidenceRole.CONTRADICTING
                && availability != MarketThesisAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Unavailable evidence cannot contradict a thesis");
        }
        if (sourceCutoff != null && observedAt.isAfter(sourceCutoff)
                && availability == MarketThesisAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Available evidence must precede source cutoff");
        }
        if (validUntil != null && validUntil.isBefore(observedAt)) {
            throw new IllegalArgumentException("validUntil cannot precede observedAt");
        }
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
