package com.hope.trading.market_intelligence.application.research.histdata;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalClosureEvidenceAdapterTest {
    @Test
    void rejectsUnsupportedClosureType() {
        UUID marketId = UUID.randomUUID();
        HistoricalClosureEvidenceSource.Evidence evidence = new HistoricalClosureEvidenceSource.Evidence(
                UUID.randomUUID(), marketId, "EUR/USD", "H1", "HISTDATA", "PROVIDER_UNAVAILABLE",
                "provider interruption", Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T12:00:00Z"), "calendar", "v1",
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                Instant.parse("2026-09-01T09:00:00Z"), "fingerprint");

        assertThatThrownBy(() -> HistoricalClosureEvidenceAdapter.toMl01(evidence, Set.of(marketId),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source contract");
    }
}
