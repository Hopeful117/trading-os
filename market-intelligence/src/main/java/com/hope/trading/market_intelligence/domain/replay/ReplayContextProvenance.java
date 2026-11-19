package com.hope.trading.market_intelligence.domain.replay;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReplayContextProvenance(
        String provider,
        EvidenceAcquisitionPolicy evidencePolicy,
        String sourceFingerprint,
        List<String> sampleProvenance,
        Instant evaluationCutoff
) {
    public ReplayContextProvenance {
        requireText(provider, "provider");
        Objects.requireNonNull(evidencePolicy, "evidence policy is required");
        requireText(sourceFingerprint, "source fingerprint");
        sampleProvenance = sampleProvenance == null ? List.of() : sampleProvenance.stream().sorted().toList();
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
    }

    public boolean matches(ReplayEvidence evidence, Instant cutoff) {
        return evidencePolicy == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                && provider.equals(evidence.provider())
                && sourceFingerprint.equals(evidence.sourceFingerprint())
                && sampleProvenance.equals(evidence.sampleProvenance())
                && evaluationCutoff.equals(cutoff);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
