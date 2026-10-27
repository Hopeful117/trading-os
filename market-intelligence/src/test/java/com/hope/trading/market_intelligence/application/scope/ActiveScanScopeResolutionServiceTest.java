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
import java.util.Optional;
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
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                capability("ZZZ", "KRAKEN"), capability("AAA", "KRAKEN")));

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
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(capability("BTC/USD", "KRAKEN")));

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
        ActiveScanScopeResolutionService target = service(accounts, marketData);
        ActiveScanScopeResolutionRequest request = new ActiveScanScopeResolutionRequest(
                accountId, "scan", List.of());

        assertThatThrownBy(() -> target.resolve(request))
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
        ActiveScanScopeResolutionService target = service(accounts, marketData);
        ActiveScanScopeResolutionRequest request = new ActiveScanScopeResolutionRequest(
                accountId, "scan", null);

        assertThatThrownBy(() -> target.resolve(request))
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
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                capability("BTC/EUR", "KRAKEN"), capability("SOL/EUR", "KRAKEN")));
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
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                new TradingCoreAccountClient.MarketCapabilityResponse(
                        "BTC/USD", true, List.of("MARKET", "LIMIT"), List.of(BigDecimal.ONE),
                        List.of(BigDecimal.ONE), 3, NOW, "KRAKEN", List.of())));

        DecisionContextResolution result = service(accounts, marketData)
                .resolveDecisionContext(accountId);

        assertThat(result.account()).isSameAs(account);
        assertThat(result.scope().effectiveScope().marketIds()).containsExactly(marketId);
        verify(accounts).findOwnedAccount(accountId);
        verify(marketData).findAllMarkets();
    }

    @Test
    void excludesTradableMarketWhenAccountBrokerCapabilityIsUnavailable() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                new TradingCoreAccountClient.MarketCapabilityResponse(
                        "BTC/USD", false, List.of(), List.of(), List.of(), 0, null, null,
                        List.of("BROKER_CAPABILITY_UNAVAILABLE"))));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(marketId, "KRAKEN", "BTC/USD", true)
        ));

        DecisionContextResolution result = service(accounts, marketData)
                .resolveDecisionContext(accountId);

        assertThat(result.scope().effectiveScope().marketIds()).isEmpty();
        assertThat(result.scope().decisions()).singleElement()
                .satisfies(decision -> assertThat(decision.reasons())
                        .containsExactly(MarketEligibilityReason.BROKER_CAPABILITY_UNAVAILABLE));
    }

    @Test
    void excludesTradableMarketWhenBrokerDoesNotSupportMarketOrders() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                new TradingCoreAccountClient.MarketCapabilityResponse(
                        "BTC/USD", true, List.of("LIMIT"), List.of(), List.of(), 0, NOW, "KRAKEN", List.of())));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(marketId, "KRAKEN", "BTC/USD", true)
        ));

        DecisionContextResolution result = service(accounts, marketData)
                .resolveDecisionContext(accountId);

        assertThat(result.scope().effectiveScope().marketIds()).isEmpty();
        assertThat(result.scope().decisions()).singleElement()
                .satisfies(decision -> assertThat(decision.reasons())
                        .containsExactly(MarketEligibilityReason.BROKER_MARKET_ORDER_UNSUPPORTED));
    }

    @Test
    void doesNotReuseCapabilityAcrossMarketProviders() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                new TradingCoreAccountClient.MarketCapabilityResponse(
                        "BTC/USD", true, List.of("MARKET"), List.of(BigDecimal.ONE),
                        List.of(BigDecimal.ONE), 3, NOW, "KRAKEN", List.of())));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(marketId, "COINBASE", "BTC/USD", true)
        ));

        DecisionContextResolution result = service(accounts, marketData)
                .resolveDecisionContext(accountId);

        assertThat(result.scope().effectiveScope().marketIds()).isEmpty();
        assertThat(result.scope().candidateMarketIds()).isEmpty();
        assertThat(result.scope().decisions()).isEmpty();
    }

    @Test
    void blocksActiveScanBeforeLoadingMarketsWhenChallengeReadinessIsBlocked() {
        UUID accountId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.challengeReadiness(accountId)).thenReturn(readiness(accountId, "ACCOUNT_BALANCE_MISMATCH", false, null));

        assertThatThrownBy(() -> service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE)))
                .isInstanceOf(ActiveScanScopeResolutionException.class)
                .hasMessageContaining("does not match");
        verifyNoInteractions(marketData);
    }

    @Test
    void challengeScopeOnlyUsesRequiredProviderAndDoesNotFallbackToKraken() {
        UUID accountId = UUID.randomUUID();
        UUID kraken = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.challengeReadiness(accountId)).thenReturn(readiness(accountId, "READY", true, "CTRADER"));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(kraken, "KRAKEN", "BTC/USD", true),
                market(ctrader, "CTRADER", "EUR/USD", true)));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(capability("EUR/USD", "CTRADER")));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.candidateMarketIds()).containsExactly(ctrader);
        assertThat(result.effectiveScope().marketIds()).containsExactly(ctrader);
    }

    @Test
    void challengeScopeFailsClosedWhenNoRequiredProviderMarketExists() {
        UUID accountId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.challengeReadiness(accountId)).thenReturn(readiness(accountId, "READY", true, "CTRADER"));
        when(marketData.findAllMarkets()).thenReturn(List.of(market(UUID.randomUUID(), "KRAKEN", "BTC/USD", true)));

        assertThatThrownBy(() -> service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE)))
                .isInstanceOf(ActiveScanScopeResolutionException.class)
                .extracting("code").isEqualTo("NO_COMPATIBLE_MARKETS");
    }

    @Test
    void selectedScopeRejectsKrakenMarketInsteadOfSilentlyDroppingIt() {
        UUID accountId = UUID.randomUUID();
        UUID kraken = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.challengeReadiness(accountId)).thenReturn(readiness(accountId, "READY", true, "CTRADER"));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(kraken, "KRAKEN", "BTC/USD", true),
                market(ctrader, "CTRADER", "EUR/USD", true)));

        assertThatThrownBy(() -> service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", List.of(kraken, ctrader), MarketScopeMode.SELECTED)))
                .isInstanceOf(ActiveScanScopeResolutionException.class)
                .extracting("code").isEqualTo("PROVIDER_MISMATCH");
    }

    @Test
    void activeScanUsesOnlyMarketsAvailableToTheSelectedAccountProvider() {
        UUID accountId = UUID.randomUUID();
        UUID kraken = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                capability("EUR/USD", "CTRADER")));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(kraken, "KRAKEN", "BTC/USD", true),
                market(ctrader, "CTRADER", "EUR/USD", true)));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.effectiveScope().marketIds()).containsExactly(ctrader);
        assertThat(result.decisions()).filteredOn(MarketEligibilityDecision::eligible)
                .extracting(MarketEligibilityDecision::marketId).containsExactly(ctrader);
    }

    @Test
    void removesOtherProvidersFromAllEligibleCandidateScope() {
        UUID accountId = UUID.randomUUID();
        UUID kraken = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(
                capability("EUR/USD", "CTRADER")));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(kraken, "KRAKEN", "BTC/USD", true),
                market(ctrader, "CTRADER", "EUR/USD", true)));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.candidateMarketIds()).containsExactly(ctrader);
        assertThat(result.decisions()).extracting(MarketEligibilityDecision::marketId)
                .containsExactly(ctrader);
    }

    @Test
    void unavailableCTraderMarketIsBootstrappedBeforeTradabilityEvaluation() {
        UUID accountId = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(capability("EUR/USD", "CTRADER")));
        when(marketData.findAllMarkets()).thenReturn(
                List.of(market(ctrader, "CTRADER", "EUR/USD", false)),
                List.of(market(ctrader, "CTRADER", "EUR/USD", true)));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(
                new com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse(
                        ctrader, "EUR/USD", new BigDecimal("1.1"), new BigDecimal("1.0"),
                        new BigDecimal("1.2"), true, NOW, "FRESH", "ticker:ctrader", 1L, NOW)));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.effectiveScope().marketIds()).containsExactly(ctrader);
        assertThat(result.decisions()).singleElement().satisfies(decision -> assertThat(decision.eligible()).isTrue());
        verify(marketData).findPriceSnapshots(argThat(request -> request.marketIds().size() == 1
                && request.marketIds().contains(ctrader)
                && "CONFIRM_OR_ACQUIRE".equals(request.acquisitionMode())));
    }

    @Test
    void unavailableCTraderMarketRemainsExcludedWhenBootstrapProducesNoQuote() {
        UUID accountId = UUID.randomUUID();
        UUID ctrader = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(capability("EUR/USD", "CTRADER")));
        when(marketData.findAllMarkets()).thenReturn(List.of(market(ctrader, "CTRADER", "EUR/USD", false)));
        when(marketData.findPriceSnapshots(any())).thenReturn(List.of(
                new com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse(
                        ctrader, "EUR/USD", null, null, null, false, null, "TIMEOUT", null, null, NOW)));

        ActiveScanScopeResolutionResult result = service(accounts, marketData).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.effectiveScope().marketIds()).isEmpty();
        assertThat(result.decisions()).singleElement().satisfies(decision -> assertThat(decision.eligible()).isFalse());
        verify(marketData).findPriceSnapshots(any());
    }

    @Test
    void strictObservedEvidenceFailureMakesMarketNotEvaluableBeforeDispatch() {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        StrictObservedMarketEvidenceGate evidenceGate = mock(StrictObservedMarketEvidenceGate.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.marketCapabilities(eq(accountId), any())).thenReturn(List.of(capability("EUR/USD", "CTRADER")));
        when(marketData.findAllMarkets()).thenReturn(List.of(market(marketId, "CTRADER", "EUR/USD", true)));
        when(evidenceGate.ruleVersion()).thenReturn("strict-observed-ohlc:v1");
        when(evidenceGate.evaluate(eq(marketId), eq(NOW))).thenReturn(Optional.of(
                new StrictObservedMarketEvidenceGate.MarketEvidenceFailure(
                        MarketEligibilityReason.DATA_UNAVAILABLE,
                        "Strict observed OHLC is unavailable")));

        ActiveScanScopeResolutionResult result = new ActiveScanScopeResolutionService(
                accounts, marketData, Clock.fixed(NOW, ZoneOffset.UTC), null, evidenceGate).resolve(
                new ActiveScanScopeResolutionRequest(accountId, "scan", null, MarketScopeMode.ALL_ELIGIBLE));

        assertThat(result.effectiveScope().marketIds()).isEmpty();
        assertThat(result.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.status()).isEqualTo(MarketEligibilityStatus.NOT_EVALUABLE);
            assertThat(decision.reasons()).containsExactly(MarketEligibilityReason.DATA_UNAVAILABLE);
        });
        assertThat(result.ruleVersions()).contains("strict-observed-ohlc:v1");
    }

    @Test
    void decisionContextReturnsStructuredBlockWhenChallengeHasNoCompatibleMarket() {
        UUID accountId = UUID.randomUUID();
        TradingCoreAccountClient accounts = mock(TradingCoreAccountClient.class);
        MarketDataClient marketData = mock(MarketDataClient.class);
        when(accounts.findOwnedAccount(accountId)).thenReturn(account(accountId));
        when(accounts.challengeReadiness(accountId)).thenReturn(readiness(accountId, "READY", true, "CTRADER"));
        when(marketData.findAllMarkets()).thenReturn(List.of(
                market(UUID.randomUUID(), "KRAKEN", "BTC/USD", true)));

        DecisionContextResolution result = service(accounts, marketData).resolveDecisionContext(accountId);

        assertThat(result.scopeStatus()).isEqualTo("NO_COMPATIBLE_MARKETS");
        assertThat(result.scope().effectiveScope().marketIds()).isEmpty();
        assertThat(result.scopeMessage()).contains("No market is available");
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

    private TradingCoreAccountClient.ChallengeReadinessResponse readiness(
            UUID accountId, String status, boolean active, String provider) {
        return new TradingCoreAccountClient.ChallengeReadinessResponse(accountId, active, status,
                status.equals("READY") ? null : "Account starting balance does not match the Challenge definition",
                active ? UUID.randomUUID() : null, active ? "1.0.0" : null, active ? "CHALLENGE" : null,
                active ? new BigDecimal("50000") : null, active ? "USD" : null,
                active ? new BigDecimal("10000") : null, BigDecimal.TEN, null, null, UUID.randomUUID(), provider, null);
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

    private TradingCoreAccountClient.MarketCapabilityResponse capability(String instrument, String provider) {
        return new TradingCoreAccountClient.MarketCapabilityResponse(
                instrument, true, List.of("MARKET"), List.of(BigDecimal.ONE), List.of(BigDecimal.ONE),
                1, NOW, provider, List.of());
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
