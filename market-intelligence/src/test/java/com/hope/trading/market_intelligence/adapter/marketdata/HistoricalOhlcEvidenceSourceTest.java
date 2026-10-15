package com.hope.trading.market_intelligence.adapter.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HistoricalOhlcEvidenceSourceTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void mapsAndFingerprintsStrictCapturedEvidence() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, 200))
                .thenReturn(series(evidence(CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(60),
                        "obs-1", "fp-1")));

        OhlcEvidenceSource.OhlcEvidenceBatch result =
                new HistoricalOhlcEvidenceSource(client).acquire(
                        MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF);

        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().getFirst().fetchedAt())
                .isEqualTo(CUTOFF.minusSeconds(60));
        assertThat(result.evidenceFingerprint()).isNotBlank();
        assertThat(result.normalizationVersion()).isEqualTo("historical-ohlc-v1");
        verify(client).findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, 200);
    }

    @Test
    void rejectsEvidenceKnownAfterCutoff() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, 200))
                .thenReturn(series(evidence(CUTOFF.minusSeconds(900), CUTOFF.plusSeconds(1),
                        "late", "late-fingerprint")));

        assertThatThrownBy(() -> new HistoricalOhlcEvidenceSource(client).acquire(
                MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict observed contract");
    }

    @Test
    void rejectsUnsupportedNormalizationVersion() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, 200))
                .thenReturn(series(evidence(CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(60),
                        "obs-1", "fp-1", "provider-normalized-v2")));

        assertThatThrownBy(() -> new HistoricalOhlcEvidenceSource(client).acquire(
                MARKET_ID, "FIFTEEN_MINUTES", 200, CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported normalization version");
    }

    private HistoricalOhlcClient.HistoricalOhlcSeriesResponse series(
            HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence) {
        return new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, List.of(evidence), "AVAILABLE", null);
    }

    private HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence(
            Instant openTime, Instant knownAt, String sourceId, String fingerprint) {
        return evidence(openTime, knownAt, sourceId, fingerprint, "historical-ohlc-v1");
    }

    private HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence(
            Instant openTime, Instant knownAt, String sourceId, String fingerprint,
            String normalizationVersion) {
        Instant closeTime = openTime.plusSeconds(900);
        return new HistoricalOhlcClient.HistoricalOhlcEvidenceResponse(
                UUID.randomUUID(), MARKET_ID, "FIFTEEN_MINUTES", openTime, closeTime,
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 2,
                true, false, "KRAKEN", "BTC/EUR", sourceId, null,
                knownAt, knownAt, knownAt, "CAPTURED", normalizationVersion, fingerprint);
    }
}
