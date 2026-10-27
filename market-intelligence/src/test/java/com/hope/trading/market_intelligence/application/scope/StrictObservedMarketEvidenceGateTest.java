package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRoleDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrictObservedMarketEvidenceGateTest {
    private static final UUID MARKET_ID = UUID.randomUUID();
    private static final Instant CUTOFF = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void acceptsRequiredRolesOnlyWhenStrictEvidenceIsValid() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(eq(MARKET_ID), eq("FOUR_HOURS"), eq(CUTOFF), eq(2)))
                .thenReturn(series("FOUR_HOURS", evidence("FOUR_HOURS", "four-hours-1"),
                        evidence("FOUR_HOURS", "four-hours-2")));
        when(client.findHistoricalOhlc(eq(MARKET_ID), eq("ONE_HOUR"), eq(CUTOFF), eq(2)))
                .thenReturn(series("ONE_HOUR", evidence("ONE_HOUR", "one-hour-1"),
                        evidence("ONE_HOUR", "one-hour-2")));

        assertThat(gate(client).evaluate(MARKET_ID, CUTOFF)).isEmpty();
    }

    @Test
    void classifiesMissingStrictEvidenceAsUnavailable() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(eq(MARKET_ID), eq("FOUR_HOURS"), eq(CUTOFF), eq(2)))
                .thenReturn(null);

        assertThat(gate(client).evaluate(MARKET_ID, CUTOFF))
                .get().extracting(StrictObservedMarketEvidenceGate.MarketEvidenceFailure::reason)
                .isEqualTo(MarketEligibilityReason.DATA_UNAVAILABLE);
    }

    @Test
    void classifiesMalformedStrictEvidenceAsIntegrityFailure() {
        HistoricalOhlcClient client = mock(HistoricalOhlcClient.class);
        when(client.findHistoricalOhlc(eq(MARKET_ID), eq("FOUR_HOURS"), eq(CUTOFF), eq(2)))
                .thenReturn(new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                        MARKET_ID, "FOUR_HOURS", CUTOFF,
                        List.of(evidence("FOUR_HOURS", "late", CUTOFF.plusSeconds(1))),
                        "AVAILABLE", null));

        assertThat(gate(client).evaluate(MARKET_ID, CUTOFF))
                .get().extracting(StrictObservedMarketEvidenceGate.MarketEvidenceFailure::reason)
                .isEqualTo(MarketEligibilityReason.DATA_INTEGRITY_FAILURE);
    }

    private StrictObservedMarketEvidenceGate gate(HistoricalOhlcClient client) {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles = new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, definition(TrendContextRole.BIAS, "FOUR_HOURS", Duration.ofHours(4)));
        roles.put(TrendContextRole.SETUP, definition(TrendContextRole.SETUP, "ONE_HOUR", Duration.ofHours(1)));
        return new StrictObservedMarketEvidenceGate(client, TrendContextProfile.conservativeSwingV1(roles));
    }

    private TrendContextRoleDefinition definition(
            TrendContextRole role, String interval, Duration duration) {
        return new TrendContextRoleDefinition(role, interval, duration, true, 2, 2);
    }

    private HistoricalOhlcClient.HistoricalOhlcSeriesResponse series(
            String interval,
            HistoricalOhlcClient.HistoricalOhlcEvidenceResponse... evidence) {
        return new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                MARKET_ID, interval, CUTOFF, List.of(evidence), "AVAILABLE", null);
    }

    private HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence(String interval, String sourceId) {
        return evidence(interval, sourceId, CUTOFF.minusSeconds(60));
    }

    private HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence(
            String interval, String sourceId, Instant knownAt) {
        Instant openTime = CUTOFF.minusSeconds(3600);
        Instant closeTime = CUTOFF.minusSeconds(900);
        return new HistoricalOhlcClient.HistoricalOhlcEvidenceResponse(
                UUID.randomUUID(), MARKET_ID, interval, openTime, closeTime,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, 1, true, false,
                "CTRADER", "EUR/USD", sourceId, null, knownAt, knownAt, knownAt,
                "CAPTURED", "historical-ohlc-v1", "fingerprint-" + sourceId);
    }
}
