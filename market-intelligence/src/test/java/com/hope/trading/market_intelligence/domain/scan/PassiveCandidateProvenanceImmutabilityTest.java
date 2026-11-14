package com.hope.trading.market_intelligence.domain.scan;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassiveCandidateProvenanceImmutabilityTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void provenanceSurvivesEveryLifecycleTransition() {
        PassiveCandidateProvenance provenance = provenance(new ArrayList<>(List.of(UUID.randomUUID())));
        ActiveScan scan = ActiveScan.readyToDispatch(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "investigate", "key", "fingerprint",
                new ActiveScanScopeSnapshot(List.of(), List.of(), List.of(), List.of(UUID.randomUUID()), NOW),
                NOW, provenance);

        ActiveScan advanced = scan.markDispatchRequested(NOW.plusSeconds(1))
                .reconcileTo(ActiveScanStatus.RUNNING, NOW.plusSeconds(2))
                .reconcileTo(ActiveScanStatus.COMPLETED, NOW.plusSeconds(3));

        assertThat(advanced.passiveCandidate()).contains(provenance);
    }

    @Test
    void provenanceListsAreDefensivelyCopied() {
        List<UUID> observations = new ArrayList<>(List.of(UUID.randomUUID()));
        PassiveCandidateProvenance provenance = provenance(observations);
        observations.add(UUID.randomUUID());

        assertThat(provenance.observationIds()).hasSize(1);
        assertThatThrownBy(() -> provenance.observationIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void legacyFactoriesCarryNoProvenance() {
        ActiveScan scan = ActiveScan.completedNoWork(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "scan", "key", "fingerprint",
                new ActiveScanScopeSnapshot(List.of(), List.of(), List.of(), List.of(), NOW), NOW);

        assertThat(scan.passiveCandidate()).isEmpty();
    }

    private PassiveCandidateProvenance provenance(List<UUID> observations) {
        UUID marketId = UUID.randomUUID();
        return new PassiveCandidateProvenance(UUID.randomUUID(), 1, UUID.randomUUID(), observations,
                UUID.randomUUID(), 1, UUID.randomUUID(), marketId, UUID.randomUUID(), marketId, "KRAKEN", "SPOT",
                NOW, NOW, NOW.plusSeconds(60), NOW, List.of());
    }
}
