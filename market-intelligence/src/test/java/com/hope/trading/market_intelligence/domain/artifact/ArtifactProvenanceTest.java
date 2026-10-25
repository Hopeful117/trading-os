package com.hope.trading.market_intelligence.domain.artifact;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactProvenanceTest {
    @Test
    void preservesEvidenceContextWhenProvenanceIsEnriched() {
        Instant cutoff = Instant.parse("2026-10-25T00:00:00Z");
        ArtifactProvenance provenance = new ArtifactProvenance(
                "market-data", "v1", UUID.randomUUID(), cutoff,
                Set.of(), Set.of(), "context", "evidence",
                EvidenceAcquisitionPolicy.STRICT_OBSERVED, 4, cutoff);

        ArtifactProvenance enriched = provenance
                .withContextFingerprint("new-context")
                .withEvidenceFingerprint("new-evidence")
                .reusedBy(UUID.randomUUID());

        assertThat(enriched.evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(enriched.observationCount()).isEqualTo(4);
        assertThat(enriched.evaluationCutoff()).isEqualTo(cutoff);
        assertThat(enriched.contextFingerprint()).isEqualTo("new-context");
        assertThat(enriched.evidenceFingerprint()).isEqualTo("new-evidence");
    }
}
