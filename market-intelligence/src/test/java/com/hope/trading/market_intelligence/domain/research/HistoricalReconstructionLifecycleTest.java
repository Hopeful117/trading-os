package com.hope.trading.market_intelligence.domain.research;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalReconstructionLifecycleTest {
    private static final Instant T0 = Instant.parse("2026-11-06T15:45:00Z");

    @Test
    void recordsDurablePartialFailureWithoutChangingAnalysisLifecycle() {
        HistoricalReconstructionLifecycle lifecycle = HistoricalReconstructionLifecycle
                .started(UUID.randomUUID(), "/safe/run", T0)
                .progress(HistoricalReconstructionLifecyclePhase.ANALYSIS_COMPLETED, null, null, T0.plusSeconds(1))
                .failure(HistoricalReconstructionLifecycleStatus.PARTIAL,
                        "OUTCOME_UNAVAILABLE", "future evidence incomplete", T0.plusSeconds(2));

        assertThat(lifecycle.status()).isEqualTo(HistoricalReconstructionLifecycleStatus.PARTIAL);
        assertThat(lifecycle.phase()).isEqualTo(HistoricalReconstructionLifecyclePhase.ANALYSIS_COMPLETED);
        assertThat(lifecycle.failureCode()).isEqualTo("OUTCOME_UNAVAILABLE");
        assertThat(lifecycle.completedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void successfulLifecycleRequiresExportPhase() {
        HistoricalReconstructionLifecycle lifecycle = HistoricalReconstructionLifecycle
                .started(UUID.randomUUID(), "/safe/run", T0)
                .success("snapshot", "outcome", T0.plusSeconds(1));

        assertThat(lifecycle.status()).isEqualTo(HistoricalReconstructionLifecycleStatus.SUCCEEDED);
        assertThat(lifecycle.phase()).isEqualTo(HistoricalReconstructionLifecyclePhase.EXPORT_COMPLETED);
        assertThat(lifecycle.snapshotFingerprint()).isEqualTo("snapshot");
    }
}
