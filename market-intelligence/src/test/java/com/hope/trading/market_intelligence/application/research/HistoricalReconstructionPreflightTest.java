package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRoleDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalReconstructionPreflightTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-11-06T15:45:00Z");

    @Test
    void requiredIncompleteRoleFailsClosed() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(MARKET_ID, "ONE_HOUR", CUTOFF, 2))
                .thenReturn(series("ONE_HOUR", "INCOMPLETE"));
        when(client.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", CUTOFF, 2))
                .thenReturn(series("FIFTEEN_MINUTES", "AVAILABLE"));

        HistoricalReconstructionPreflight preflight = new HistoricalReconstructionPreflight(client, profile());

        assertThatThrownBy(() -> preflight.verify(MARKET_ID, CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void requiredRoleGapFailsClosed() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        var response = seriesWithGap("ONE_HOUR");
        when(client.findHistoricalOhlc(MARKET_ID, "ONE_HOUR", CUTOFF, 2))
                .thenReturn(response);

        HistoricalReconstructionPreflight preflight = new HistoricalReconstructionPreflight(client, profile());

        assertThatThrownBy(() -> preflight.verify(MARKET_ID, CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gap");
    }

    private TrendContextProfile profile() {
        return TrendContextProfile.conservativeSwingV1(Map.of(
                TrendContextRole.BIAS, new TrendContextRoleDefinition(
                        TrendContextRole.BIAS, "ONE_HOUR", Duration.ofHours(1), true, 2, 2),
                TrendContextRole.SETUP, new TrendContextRoleDefinition(
                        TrendContextRole.SETUP, "FIFTEEN_MINUTES", Duration.ofMinutes(15), true, 2, 2)));
    }

    private HistoricalOhlcClient.HistoricalOhlcSeriesResponse series(String interval, String status) {
        return new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                MARKET_ID, interval, CUTOFF, java.util.List.of(), status, null);
    }

    private HistoricalOhlcClient.HistoricalOhlcSeriesResponse seriesWithGap(String interval) {
        return new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                MARKET_ID, interval, CUTOFF,
                java.util.List.of(evidence(interval, CUTOFF.minus(Duration.ofHours(3))),
                        evidence(interval, CUTOFF.minus(Duration.ofHours(1)))),
                "AVAILABLE", null);
    }

    private HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence(String interval, Instant open) {
        var evidence = mock(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse.class);
        when(evidence.marketId()).thenReturn(MARKET_ID);
        when(evidence.interval()).thenReturn(interval);
        when(evidence.openTime()).thenReturn(open);
        when(evidence.closeTime()).thenReturn(open.plus(interval.equals("ONE_HOUR")
                ? Duration.ofHours(1) : Duration.ofMinutes(15)));
        when(evidence.closed()).thenReturn(true);
        when(evidence.synthetic()).thenReturn(false);
        when(evidence.observationId()).thenReturn(UUID.randomUUID());
        when(evidence.sourceId()).thenReturn("source");
        when(evidence.fetchedAt()).thenReturn(CUTOFF.minusSeconds(1));
        when(evidence.capturedAt()).thenReturn(CUTOFF.minusSeconds(1));
        when(evidence.knownAt()).thenReturn(CUTOFF.minusSeconds(1));
        when(evidence.origin()).thenReturn("CAPTURED");
        when(evidence.normalizationVersion()).thenReturn("historical-ohlc-v1");
        when(evidence.observationFingerprint()).thenReturn(UUID.randomUUID().toString());
        return evidence;
    }
}
