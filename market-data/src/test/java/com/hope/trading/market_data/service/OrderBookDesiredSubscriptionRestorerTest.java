package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.OrderBookAcquisitionProperties;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.websocket.KrakenStreamLifecycleDiagnostics;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.repository.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderBookDesiredSubscriptionRestorerTest {
    private static final UUID MARKET_ID = UUID.randomUUID();

    @Mock
    private MarketRepository marketRepository;
    @Mock
    private MarketSubscriptionService subscriptionService;
    @Mock
    private KrakenStreamLifecycleDiagnostics diagnostics;

    private OrderBookAcquisitionProperties properties;
    private OrderBookAcquisitionProperties.DesiredSubscription desired;
    private OrderBookDesiredSubscriptionRestorer restorer;
    private Market market;

    @BeforeEach
    void setUp() {
        properties = new OrderBookAcquisitionProperties();
        properties.setStartupEnabled(true);
        desired = new OrderBookAcquisitionProperties.DesiredSubscription();
        desired.setProvider("KRAKEN");
        desired.setSymbol("BTC/USD");
        desired.setProductType("LINEAR_PERPETUAL");
        desired.setProviderSymbol("PF_XBTUSD");
        desired.setDepth("25");
        properties.setDesiredSubscriptions(List.of(desired));

        market = Market.builder()
                .marketId(MARKET_ID)
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .providerSymbol("PF_XBTUSD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .marketState(MarketState.builder().tradable(true).build())
                .build();

        restorer = new OrderBookDesiredSubscriptionRestorer(
                properties, marketRepository, subscriptionService, diagnostics);
    }

    @Test
    void restoresConfiguredSubscriptionThroughCanonicalService() {
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL))
                .thenReturn(Optional.of(market));

        restorer.restoreConfiguredSubscriptions();

        verify(subscriptionService).subscribe(eq(MARKET_ID), any());
        verify(diagnostics).desiredSubscriptionRestoreStarted(
                eq("KRAKEN"), eq("LINEAR_PERPETUAL"), eq("BTC/USD"), eq("PF_XBTUSD"), eq("25"));
        verify(diagnostics).desiredSubscriptionRestored(
                eq("KRAKEN"), eq("LINEAR_PERPETUAL"), eq("BTC/USD"), eq("PF_XBTUSD"), eq("25"));
    }

    @Test
    void repeatedStartupRestorationDoesNotDuplicateSubscription() {
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL))
                .thenReturn(Optional.of(market));

        restorer.restoreConfiguredSubscriptions();
        restorer.restoreConfiguredSubscriptions();

        verify(subscriptionService, times(1)).subscribe(eq(MARKET_ID), any());
    }

    @Test
    void disabledRestorationDoesNotContactCatalogueOrProvider() {
        properties.setStartupEnabled(false);

        restorer.restoreConfiguredSubscriptions();

        verifyNoInteractions(marketRepository, subscriptionService, diagnostics);
    }

    @Test
    void startupFailureDoesNotBecomeRestoredAndCanBeObservedAgain() {
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL))
                .thenReturn(Optional.of(market));
        doThrow(new IllegalStateException("provider unavailable"))
                .when(subscriptionService).subscribe(eq(MARKET_ID), any());

        restorer.restoreConfiguredSubscriptions();
        restorer.restoreConfiguredSubscriptions();

        verify(subscriptionService, times(2)).subscribe(eq(MARKET_ID), any());
        verify(diagnostics, times(2)).desiredSubscriptionRestoreFailed(
                eq("KRAKEN"), eq("LINEAR_PERPETUAL"), eq("BTC/USD"), eq("PF_XBTUSD"), eq("25"),
                eq("SUBSCRIPTION"), any());
    }

    @Test
    void shutdownReleasesOnlyTheRestoredSubscription() {
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL))
                .thenReturn(Optional.of(market));

        restorer.restoreConfiguredSubscriptions();
        restorer.releaseRestoredSubscriptions();

        verify(subscriptionService).unsubscribe(eq(MARKET_ID), any());
    }

    @Test
    void rejectsWrongProviderSymbolBeforeProviderCall() {
        desired.setProviderSymbol("WRONG");
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "BTC/USD", InstrumentProductType.LINEAR_PERPETUAL))
                .thenReturn(Optional.of(market));

        restorer.restoreConfiguredSubscriptions();

        verify(subscriptionService, never()).subscribe(any(), any());
        verify(diagnostics).desiredSubscriptionRestoreFailed(
                eq("KRAKEN"), eq("LINEAR_PERPETUAL"), eq("BTC/USD"), eq("WRONG"), eq("25"),
                eq("VALIDATION"), any());
    }

    @Test
    void treatsMalformedConfigurationAsBoundedValidationFailure() {
        desired.setProvider("NOT_A_PROVIDER");

        restorer.restoreConfiguredSubscriptions();

        verifyNoInteractions(marketRepository, subscriptionService);
        verify(diagnostics).desiredSubscriptionRestoreFailed(
                eq("NOT_A_PROVIDER"), eq("LINEAR_PERPETUAL"), eq("BTC/USD"), eq("PF_XBTUSD"),
                eq("25"), eq("VALIDATION"), any());
    }
}
