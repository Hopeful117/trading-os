package com.hope.trading.news.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFactNormalizationTest {
    @Test
    void defaultsOptionalCollectionsAndUnknownClassifications() {
        EconomicEvent event = new EconomicEvent(
                UUID.randomUUID(), "fixture", "event-1", "CPI", null,
                Instant.parse("2026-01-01T12:00:00Z"), null, null, null,
                null, null, null, null, null, null, null, null);

        assertThat(event.currencies()).isEmpty();
        assertThat(event.marketIds()).isEmpty();
        assertThat(event.impact()).isEqualTo(ImpactLevel.UNKNOWN);
        assertThat(event.status()).isEqualTo(EconomicEventStatus.UNKNOWN);
        assertThat(event.normalizationVersion()).isEqualTo("v1");
    }
}
