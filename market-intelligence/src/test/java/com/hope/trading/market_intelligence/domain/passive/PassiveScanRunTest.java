package com.hope.trading.market_intelligence.domain.passive;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassiveScanRunTest {
    private final Instant start = Instant.parse("2026-11-13T00:00:00Z");
    private final Instant end = start.plusSeconds(300);

    @Test
    void advancesThroughDurableLifecycle() {
        PassiveScanRun run = PassiveScanRun.requested(UUID.randomUUID(), "run-key", "fingerprint",
                "SYSTEM", start, end, "v1", "sha256-v1", "universe", start, List.of(), start);

        PassiveScanRun current = run.advanceTo(PassiveScanRunStatus.DISPATCH_REQUESTED, start.plusSeconds(1))
                .advanceTo(PassiveScanRunStatus.RUNNING, start.plusSeconds(2))
                .advanceTo(PassiveScanRunStatus.COMPLETED, start.plusSeconds(3));

        assertThat(current.status()).isEqualTo(PassiveScanRunStatus.COMPLETED);
        assertThat(current.advanceTo(PassiveScanRunStatus.FAILED, end)).isSameAs(current);
    }

    @Test
    void rejectsInvalidTransition() {
        PassiveScanRun run = PassiveScanRun.requested(UUID.randomUUID(), "run-key", "fingerprint",
                "SYSTEM", start, end, "v1", "sha256-v1", "universe", start, List.of(), start);

        assertThatThrownBy(() -> run.advanceTo(PassiveScanRunStatus.COMPLETED, start.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
