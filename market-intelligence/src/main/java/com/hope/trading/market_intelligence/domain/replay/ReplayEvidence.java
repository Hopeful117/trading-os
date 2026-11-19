package com.hope.trading.market_intelligence.domain.replay;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReplayEvidence(
        ReplayEvidenceStatus status,
        String provider,
        String sourceFingerprint,
        List<ReplayEvidenceSample> samples,
        List<String> diagnostics
) {
    public ReplayEvidence {
        Objects.requireNonNull(status, "evidence status is required");
        requireText(provider, "provider");
        requireText(sourceFingerprint, "source fingerprint");
        samples = samples == null ? List.of() : samples.stream()
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(value -> value.observationFingerprint()))
                .toList();
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (status == ReplayEvidenceStatus.COMPLETE && samples.isEmpty()) {
            throw new IllegalArgumentException("complete evidence requires samples");
        }
        if (samples.stream().anyMatch(value -> !provider.equals(value.provider()))) {
            throw new IllegalArgumentException("evidence sample provider does not match evidence provider");
        }
    }

    public boolean admissibleAt(Instant cutoff) {
        return status == ReplayEvidenceStatus.COMPLETE
                && samples.stream().allMatch(value -> value.admissibleAt(cutoff));
    }

    public Instant latestKnownAt() {
        return samples.stream()
                .map(ReplayEvidenceSample::knownAt)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public List<String> selectedEvidence() {
        return samples.stream().map(ReplayEvidenceSample::observationFingerprint).toList();
    }

    public List<String> sampleProvenance() {
        return samples.stream()
                .map(value -> value.observationFingerprint().length() + ":"
                        + value.observationFingerprint() + value.normalizationVersion().length() + ":"
                        + value.normalizationVersion())
                .toList();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
