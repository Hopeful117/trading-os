package com.hope.trading.market_intelligence.domain.scan;

import java.util.Objects;

/**
 * Deterministic comparison of one passive evidence dimension against the active
 * investigation's needs. Story 0243 (decision D1): passive evidence is referenced
 * only; active analysis always reacquires through its existing acquisition policy.
 */
public record PassiveEvidenceAssessment(Dimension dimension, Status status, String reason) {
    public PassiveEvidenceAssessment {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason;
    }

    public enum Dimension { EVIDENCE_POLICY, CAPABILITIES, CUTOFF, FRESHNESS }

    public enum Status { COMPATIBLE_REFERENCE_ONLY, INCOMPATIBLE_REACQUIRED }
}
