package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void usesRequestCutoffAndExcludesFutureEvidenceFromContextHistory() {
        Instant cutoff = BOUNDARY.minus(Duration.ofMinutes(5));
        MarketDataClient client = mock(MarketDataClient.class);
        OhlcResponse futureBias = response("FOUR_HOURS", cutoff.plusSeconds(1), "future-bias");
        OhlcResponse pastBias = responseAt("FOUR_HOURS", cutoff.minus(Duration.ofHours(5)), "past-bias", cutoff);
        OhlcResponse pastSetup = responseAt("ONE_HOUR", cutoff.minus(Duration.ofHours(2)), "past-setup", cutoff);
        OhlcResponse pastTrigger = responseAt("FIFTEEN_MINUTES", cutoff.minus(Duration.ofMinutes(30)), "past-trigger", cutoff);
        when(client.findOhlc(MARKET_ID, "FOUR_HOURS", 1)).thenReturn(List.of(pastBias, futureBias));
        when(client.findOhlc(MARKET_ID, "ONE_HOUR", 1)).thenReturn(List.of(pastSetup));
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 1)).thenReturn(List.of(pastTrigger));

        TrendContextRoleHistoryContextContributor contributor =
                new TrendContextRoleHistoryContextContributor(
                        client, new TrendContextInputMapper(), profile(),
                        Clock.fixed(BOUNDARY, ZoneOffset.UTC));

        ContextSection section = contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "trend-context",
                null, null, null, cutoff));
        TrendContextRoleHistory history = (TrendContextRoleHistory) section.payload();

        assertThat(history.cutOffAt()).isEqualTo(cutoff);
        assertThat(history.assessmentAt()).isEqualTo(cutoff);
        assertThat(history.responsesByRole().get(TrendContextRole.BIAS))
                .extracting(OhlcResponse::sourceId)
                .containsExactly("past-bias");
        assertThat(history.responsesByRole().values().stream()
                .flatMap(Collection::stream)
                .map(OhlcResponse::sourceId))
                .doesNotContain("future-bias");

        TrendContextAssessmentInput input = new TrendContextInputMapper().map(
                history.responsesByRole(), profile(), cutoff, cutoff, "trend-context-rules-v1");
        TrendContextAssessment assessment = new TrendContextEngine().assess(input, Map.of());
        assertThat(assessment.cutOffAt()).isEqualTo(cutoff);
        assertThat(assessment.inputFingerprint()).isEqualTo(input.fingerprint());
    }

    @Test
    void liveEvidenceMayBeFetchedAfterCutoffWhenMarketTimestampsAreHistorical() {
        Instant cutoff = BOUNDARY.minus(Duration.ofMinutes(5));
        Instant fetchedAt = cutoff.plusSeconds(2);
        MarketDataClient client = mock(MarketDataClient.class);
        when(client.findOhlc(MARKET_ID, "FOUR_HOURS", 1)).thenReturn(
                List.of(responseAt("FOUR_HOURS", cutoff.minus(Duration.ofHours(5)), "bias", fetchedAt)));
        when(client.findOhlc(MARKET_ID, "ONE_HOUR", 1)).thenReturn(
                List.of(responseAt("ONE_HOUR", cutoff.minus(Duration.ofHours(2)), "setup", fetchedAt)));
        when(client.findOhlc(MARKET_ID, "FIFTEEN_MINUTES", 1)).thenReturn(
                List.of(responseAt("FIFTEEN_MINUTES", cutoff.minus(Duration.ofMinutes(30)), "trigger", fetchedAt)));

        TrendContextRoleHistoryContextContributor contributor =
                new TrendContextRoleHistoryContextContributor(
                        client, new TrendContextInputMapper(), profile(),
                        Clock.fixed(BOUNDARY, ZoneOffset.UTC));

        ContextSection section = contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "trend-context",
                null, null, null, cutoff, EvidenceAcquisitionPolicy.LIVE_CURRENT));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(((TrendContextRoleHistory) section.payload()).responsesByRole())
                .containsKeys(TrendContextRole.BIAS, TrendContextRole.SETUP, TrendContextRole.TRIGGER);
    }

    @Test
    void classifiesHistoryWithNoEvidenceAtCutoffAsIncomplete() {
        Instant cutoff = BOUNDARY.minus(Duration.ofDays(1));
        MarketDataClient client = mock(MarketDataClient.class);
        OhlcResponse future = response("FOUR_HOURS", cutoff.plusSeconds(1), "future");
        when(client.findOhlc(any(), any(), anyInt())).thenReturn(List.of(future));

        TrendContextRoleHistoryContextContributor contributor =
                new TrendContextRoleHistoryContextContributor(
                        client, new TrendContextInputMapper(), profile(),
                        Clock.fixed(BOUNDARY, ZoneOffset.UTC));

        ContextSection section = contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), MARKET_ID, AnalysisExecutionMode.ACTIVE, "trend-context",
                null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.message()).contains("incomplete");
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
        return responseAt(interval, openTime, sourceId, BOUNDARY);
    }

    private OhlcResponse responseAt(String interval, Instant openTime, String sourceId, Instant fetchedAt) {
        return new OhlcResponse(
                MARKET_ID, "KRAKEN", "BTC/EUR", interval, openTime,
                openTime.plus(Duration.ofMinutes(interval.equals("FOUR_HOURS") ? 240
                        : interval.equals("ONE_HOUR") ? 60 : 15)),
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("95"),
                new BigDecimal("102"), BigDecimal.TEN, new BigDecimal("101"), 2,
                true, openTime, false, sourceId, fetchedAt);
    }
}
