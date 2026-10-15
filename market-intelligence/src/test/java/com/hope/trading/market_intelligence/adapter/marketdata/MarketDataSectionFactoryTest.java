package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataSectionFactoryTest {
    @Test
    void identifiesStaleContext() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        MarketSnapshotContext payload = new MarketSnapshotContext(
                UUID.randomUUID(), "BTC/USD", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, true,
                Instant.now().minusSeconds(60)
        );

        ContextSection section = factory.available(
                ContextSectionType.MARKET_SNAPSHOT,
                payload,
                payload.occurredAt()
        );

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(section.provenance().source()).isEqualTo("market-data");
    }

    @Test
    void keepsMarketSnapshotFreshnessAsReportedByMarketData() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        MarketSnapshotContext payload = new MarketSnapshotContext(
                UUID.randomUUID(), "BTC/USD", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, true,
                Instant.parse("2026-08-01T12:00:00Z")
        );

        ContextSection section = factory.snapshot(
                payload,
                "STALE",
                payload.occurredAt(),
                Instant.parse("2026-08-01T12:00:02Z")
        );

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(section.message()).isEqualTo("Current market snapshot is stale");
        assertThat(section.provenance().fetchedAt()).isEqualTo("2026-08-01T12:00:02Z");
    }

    @Test
    void excludesMarketDataNewerThanEvaluationCutoff() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        Instant cutoff = Instant.parse("2026-08-01T12:00:00Z");

        ContextSection section = factory.availableAt(
                ContextSectionType.HISTORICAL_OHLC, null,
                cutoff.plusSeconds(1), cutoff.plusSeconds(2), cutoff);

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.message()).contains("evaluation cut-off");
    }

    @Test
    void excludesMarketSnapshotsNewerThanEvaluationCutoff() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        Instant cutoff = Instant.parse("2026-08-01T12:00:00Z");

        ContextSection section = factory.snapshotAt(
                null, "AVAILABLE", cutoff.plusSeconds(1), cutoff.plusSeconds(2), cutoff);

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.type()).isEqualTo(ContextSectionType.MARKET_SNAPSHOT);
        assertThat(section.message()).contains("evaluation cut-off");
    }

    @Test
    void usesClockWhenMarketDataOccurredAtIsUnknown() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));

        ContextSection section = factory.available(
                ContextSectionType.HISTORICAL_OHLC, null, null);

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.provenance().sourceOccurredAt()).isNull();
    }

    @Test
    void usesClockWhenSnapshotFetchTimeIsUnknown() {
        MarketDataSectionFactory factory =
                new MarketDataSectionFactory(Duration.ofSeconds(30));
        Instant occurredAt = Instant.now();

        ContextSection section = factory.snapshot(null, "AVAILABLE", occurredAt, null);

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(section.provenance().fetchedAt()).isNotNull();
    }
}
