package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
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
                new ActiveScanScopeResolutionRequest(accountId, "scan", List.of()));

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

    private ActiveScanScopeResolutionService service(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData
    ) {
        return new ActiveScanScopeResolutionService(accounts, marketData,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TradingCoreAccountClient.TradingCoreAccountResponse account(UUID accountId) {
        return new TradingCoreAccountClient.TradingCoreAccountResponse(
                accountId, "Main", "EUR", BigDecimal.ONE, BigDecimal.ONE, UUID.randomUUID(),
                UUID.randomUUID());
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
