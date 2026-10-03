package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactsResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.*;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ActiveScanScopeResolutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T10:15:30Z");

    @Test
    void resolvesEffectiveScopeFromTradableCatalogMarketsInDeterministicOrder() {
        UUID accountId = UUID.randomUUID();
        UUID zzz = UUID.randomUUID();
        UUID aaa = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(zzz, "KRAKEN", "ZZZ", true),
                market(aaa, "KRAKEN", "AAA", true)
        ));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.candidateMarketIds()).containsExactly(aaa, zzz);
        assertThat(result.effectiveScope().marketIds()).containsExactly(aaa, zzz);
        assertThat(result.decisions()).extracting(MarketEligibilityDecision::marketId)
                .containsExactly(aaa, zzz);
    }

    @Test
    void requestedMarketsAreDeduplicatedAndUnknownOrClosedMarketsAreExcluded() {
        UUID accountId = UUID.randomUUID();
        UUID tradable = UUID.randomUUID();
        UUID closed = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(tradable, "KRAKEN", "BTC/USD", true),
                market(closed, "KRAKEN", "ETH/USD", false)
        ));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(
                        accountId,
                        "scan",
                        List.of(tradable, tradable, unknown, closed)
                ));

        assertThat(result.requestedMarketIds()).containsExactly(tradable, unknown, closed);
        assertThat(result.candidateMarketIds()).containsExactly(tradable, unknown, closed);
        assertThat(result.effectiveScope().marketIds()).containsExactly(tradable);
        assertThat(result.decisions()).extracting(MarketEligibilityDecision::reasons)
                .containsExactly(
                        List.of(),
                        List.of(MarketEligibilityReason.MARKET_NOT_FOUND),
                        List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE)
                );
    }

    @Test
    void rejectsUnauthorizedOrMissingAccountBeforeScanningMarkets() {
        UUID accountId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenThrow(notFoundException());

        assertThatThrownBy(() -> service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", List.of())))
                .isInstanceOf(ActiveScanScopeResolutionException.class)
                .hasMessageContaining("Account is not available");
        verifyNoInteractions(marketData);
    }

    @Test
    void nullOrEmptyLegacyScopeNeverExpandsToTheCatalogue() {
        UUID accountId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));

        assertThatThrownBy(() -> service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null)))
                .isInstanceOf(ActiveScanScopeResolutionException.class)
                .hasMessageContaining("explicit SELECTED or ALL_ELIGIBLE");
        verifyNoInteractions(marketData);
    }

    @Test
    void factsAreRequestedOnlyAfterCheapGatesAndBudgetExhaustionIsNotEvaluable() {
        UUID accountId = UUID.randomUUID();
        UUID tradable = UUID.randomUUID();
        UUID closed = UUID.randomUUID();
        UUID deferred = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(tradable, "KRAKEN", "BTC/EUR", true),
                market(closed, "KRAKEN", "ETH/EUR", false),
                market(deferred, "KRAKEN", "SOL/EUR", true)));
        when(marketData.findMarketFacts(eq(tradable), any(), anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(availableFacts(tradable));

        MarketEligibilityProperties properties = new MarketEligibilityProperties();
        properties.setInterval(com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval.ONE_HOUR);
        properties.setActivityWindowMinutes(60);
        properties.setReadinessLookbackCandles(24);
        properties.setMinimumCompletedCandles(12);
        properties.setMaxObservationAgeSeconds(300);
        properties.setMaxMarketFactEvaluationsPerScan(1);

        ActiveScanScopeResolutionResult result = new ActiveScanScopeResolutionService(
                accounts, marketData, Clock.fixed(NOW, ZoneOffset.UTC),
                new MarketEligibilityPolicy(properties)).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan",
                        List.of(tradable, closed, deferred)));

        assertThat(result.effectiveScope().marketIds()).containsExactly(tradable);
        assertThat(result.decisions()).extracting(MarketEligibilityDecision::status)
                .containsExactly(MarketEligibilityStatus.ELIGIBLE,
                        MarketEligibilityStatus.EXCLUDED, MarketEligibilityStatus.NOT_EVALUABLE);
        verify(marketData, times(1)).findMarketFacts(eq(tradable), any(), anyLong(), anyInt(), anyInt(), anyLong());
        verify(marketData, never()).findMarketFacts(eq(closed), any(), anyLong(), anyInt(), anyInt(), anyLong());
        verify(marketData, never()).findMarketFacts(eq(deferred), any(), anyLong(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void resolvesDecisionContextWithAccountFactsAndEffectiveMarkets() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        TradingCoreAccountClient.TradingCoreAccountResponse account = account(accountId);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account);
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(marketId, "KRAKEN", "BTC/USD", true)
        ));

        DecisionContextResolution result = service(accounts, marketData)
                .resolveDecisionContext(accountId);

        assertThat(result.account()).isSameAs(account);
        assertThat(result.scope().effectiveScope().marketIds()).containsExactly(marketId);
        verify(accounts).findOwnedAccount(accountId);
        verify(marketData).findAllMarkets();
    }

    private ActiveScanScopeResolutionService service(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData
    ) {
        return new ActiveScanScopeResolutionService(accounts, marketData,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TradingCoreAccountClient.TradingCoreAccountResponse account(UUID accountId) {
        return new TradingCoreAccountClient.TradingCoreAccountResponse(
                accountId, UUID.randomUUID(), "Main", "EUR", BigDecimal.ONE, BigDecimal.ONE,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "1.0.0", UUID.randomUUID(), 1L);
    }

    private MarketResponse market(UUID marketId, String provider, String symbol, boolean tradable) {
        String[] parts = symbol.contains("/") ? symbol.split("/") : new String[] {symbol, symbol};
        return new MarketResponse(
                marketId,
                provider,
                symbol,
                parts[0],
                parts[1],
                new MarketResponse.MarketStateResponse(
                        "OPEN", tradable, null, NOW
                )
        );
    }

    private MarketFactsResponse availableFacts(UUID marketId) {
        return new MarketFactsResponse(marketId, "BTC/EUR", "BTC", "EUR", NOW,
                new MarketFactsResponse.MarketActivityResponse(
                        marketId, "KRAKEN", "BTC/EUR", "BTC", "EUR",
                        com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval.ONE_HOUR,
                        Duration.ofHours(24), NOW.minus(Duration.ofHours(24)), NOW, NOW,
                        Duration.ofMinutes(5), null, 24, 24, 24, 0, 0, 0,
                        com.hope.trading.market_intelligence.adapter.marketdata.MarketFactStatus.AVAILABLE,
                        null, "market-facts-v1"),
                new MarketFactsResponse.MarketReadinessResponse(
                        marketId,
                        com.hope.trading.market_intelligence.adapter.marketdata.OhlcInterval.ONE_HOUR,
                        24, 24, 12, 24, 24, 0, 0, 0, 0, 0,
                         NOW, NOW, NOW, Duration.ofMinutes(5),
                         com.hope.trading.market_intelligence.adapter.marketdata.MarketFactStatus.AVAILABLE,
                         null, "market-facts-v1"));
    }

    private FeignException.NotFound notFoundException() {
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://trading-core/api/v1/accounts/" + UUID.randomUUID(),
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .build();
        return (FeignException.NotFound) FeignException.errorStatus("TradingCoreAccountClient#findOwnedAccount", response);
    }
}
