package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrendContextRoleHistoryContextContributorTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant BOUNDARY = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void acquiresConfiguredRolesInsideOneStableBoundary() {
        MarketDataClient client = mock(MarketDataClient.class);
        OhlcResponse bias = response("FOUR_HOURS", BOUNDARY.minus(Duration.ofHours(4)), "bias");
        OhlcResponse setup = response("ONE_HOUR", BOUNDARY.minus(Duration.ofHours(1)), "setup");
        OhlcResponse trigger = response("FIFTEEN_MINUTES", BOUNDARY.minus(Duration.ofMinutes(15)), "trigger");
        when(client.findOhlc(MARKET_ID, "FOUR_HOURS", 1)).thenReturn(List.of(bias));
        when(client.findOhlc(MARKET_ID, "ONE_HOUR", 1)).thenReturn(List.of(setup));
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 1)).thenReturn(List.of(trigger));

        TrendContextRoleHistoryContextContributor contributor =
                new TrendContextRoleHistoryContextContributor(
                        client, new TrendContextInputMapper(), profile(),
                        Clock.fixed(BOUNDARY, ZoneOffset.UTC));

        ContextSection section = contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "trend-context"));
        TrendContextRoleHistory history = (TrendContextRoleHistory) section.payload();

        assertThat(history.assessmentAt()).isEqualTo(BOUNDARY);
        assertThat(history.cutOffAt()).isEqualTo(BOUNDARY);
        assertThat(history.responsesByRole()).containsKeys(
                TrendContextRole.BIAS, TrendContextRole.SETUP, TrendContextRole.TRIGGER);
        verify(client).findOhlc(MARKET_ID, "FOUR_HOURS", 1);
        verify(client).findOhlc(MARKET_ID, "ONE_HOUR", 1);
        verify(client).findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 1);
    }

    private TrendContextProfile profile() {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles =
                new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, new TrendContextRoleDefinition(
                TrendContextRole.BIAS, "FOUR_HOURS", Duration.ofHours(4), true, 1, 1));
        roles.put(TrendContextRole.SETUP, new TrendContextRoleDefinition(
                TrendContextRole.SETUP, "ONE_HOUR", Duration.ofHours(1), true, 1, 1));
        roles.put(TrendContextRole.TRIGGER, new TrendContextRoleDefinition(
                TrendContextRole.TRIGGER, "FIFTEEN_MINUTES", Duration.ofMinutes(15), false, 1, 1));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private OhlcResponse response(String interval, Instant openTime, String sourceId) {
        return new OhlcResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", interval, openTime,
                openTime.plus(Duration.ofMinutes(interval.equals("FOUR_HOURS") ? 240
                        : interval.equals("ONE_HOUR") ? 60 : 15)),
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 2,
                true, openTime, false, sourceId, BOUNDARY);
    }
}
