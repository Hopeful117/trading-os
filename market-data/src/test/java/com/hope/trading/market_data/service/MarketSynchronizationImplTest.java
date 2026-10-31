package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerclient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.repository.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3B: protects the market-catalogue synchronization contract —
 * new provider markets are inserted, existing rows are updated in place
 * (identity preserved), and the operation is repeatable.
 */
class MarketSynchronizationImplTest {

    private final MarketDataProvider marketDataProvider =
            mock(MarketDataProvider.class);
    private final MarketRepository marketRepository = mock(MarketRepository.class);

    private MarketSynchronizationImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketSynchronizationImpl(marketDataProvider, marketRepository);
        when(marketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Market market(String symbol, String base, String quote) {
        Market market = new Market();
        market.setProvider(MarketProvider.KRAKEN);
        market.setSymbol(symbol);
        market.setBaseAsset(base);
        market.setQuoteAsset(quote);
        return market;
    }

    @Test
    void newMarketsAreInserted() {
        Market fresh = market("XBT/EUR", "XBT", "EUR");
        when(marketDataProvider.getMarkets()).thenReturn(List.of(fresh));
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "XBT/EUR", InstrumentProductType.SPOT)).thenReturn(Optional.empty());

        service.synchronizeMarkets();

        verify(marketRepository).saveAll(List.of(fresh));
    }

    @Test
    void existingMarketIsUpdatedInPlaceWithoutIdentityChange() {
        Market existing = market("XBT/EUR", "OLD", "OLD");
        existing.setProviderSymbol("OLD_SYMBOL");
        Market incoming = market("XBT/EUR", "XBT", "EUR");
        incoming.setProviderSymbol("NEW_SYMBOL");
        when(marketDataProvider.getMarkets()).thenReturn(List.of(incoming));
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "XBT/EUR", InstrumentProductType.SPOT)).thenReturn(Optional.of(existing));

        service.synchronizeMarkets();

        verify(marketRepository).saveAll(List.of(existing));
        assertThat(existing.getBaseAsset()).isEqualTo("XBT");
        assertThat(existing.getQuoteAsset()).isEqualTo("EUR");
        assertThat(existing.getProviderSymbol()).isEqualTo("NEW_SYMBOL");
    }

    @Test
    void existingCfdMarketKeepsItsCompleteFinancialContractMetadata() {
        Market existing = market("EUR/USD", "OLD", "OLD");
        existing.setProductType(InstrumentProductType.CFD);
        Market incoming = market("EUR/USD", "EUR", "USD");
        incoming.setProductType(InstrumentProductType.CFD);
        incoming.setQuantityUnit("UNDERLYING_UNITS");
        incoming.setContractMultiplier(new BigDecimal("100000"));
        incoming.setContractSourceId("market-data-contract:EUR/USD");
        incoming.setContractSourceVersion(3L);
        incoming.setContractObservedAt(Instant.parse("2026-10-31T12:00:00Z"));
        when(marketDataProvider.getMarkets()).thenReturn(List.of(incoming));
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "EUR/USD", InstrumentProductType.CFD)).thenReturn(Optional.of(existing));

        service.synchronizeMarkets();

        assertThat(existing.getQuantityUnit()).isEqualTo("UNDERLYING_UNITS");
        assertThat(existing.getContractMultiplier()).isEqualByComparingTo("100000");
        assertThat(existing.getContractSourceId()).isEqualTo("market-data-contract:EUR/USD");
        assertThat(existing.getContractSourceVersion()).isEqualTo(3L);
        assertThat(existing.getContractObservedAt()).isEqualTo(Instant.parse("2026-10-31T12:00:00Z"));
    }

    @Test
    void synchronizationIsRepeatableWithoutDuplication() {
        Market existing = market("XBT/EUR", "XBT", "EUR");
        when(marketDataProvider.getMarkets()).thenReturn(List.of(existing));
        when(marketRepository.findByProviderAndSymbolAndProductType(
                MarketProvider.KRAKEN, "XBT/EUR", InstrumentProductType.SPOT)).thenReturn(Optional.of(existing));

        service.synchronizeMarkets();
        service.synchronizeMarkets();

        // Both passes update the same persisted row: no duplicates inserted.
        verify(marketRepository, times(2)).saveAll(anyList());
    }

    @Test
    void synchronizesMarketsFromEveryRegisteredProvider() {
        MarketDataProvider cTraderProvider = mock(MarketDataProvider.class);
        Market krakenMarket = market("XBT/EUR", "XBT", "EUR");
        Market cTraderMarket = market("EUR/USD", "EUR", "USD");
        cTraderMarket.setProvider(MarketProvider.CTRADER);

        when(marketDataProvider.getName()).thenReturn(MarketProvider.KRAKEN);
        when(cTraderProvider.getName()).thenReturn(MarketProvider.CTRADER);
        when(marketDataProvider.getMarkets()).thenReturn(List.of(krakenMarket));
        when(cTraderProvider.getMarkets()).thenReturn(List.of(cTraderMarket));
        when(marketRepository.findByProviderAndSymbolAndProductType(any(), any(), any()))
                .thenReturn(Optional.empty());

        service = new MarketSynchronizationImpl(List.of(marketDataProvider, cTraderProvider), marketRepository);
        service.synchronizeMarkets();

        verify(marketRepository).saveAll(argThat(markets -> {
            List<Market> saved = java.util.stream.StreamSupport.stream(
                    markets.spliterator(), false).toList();
            return saved.containsAll(List.of(krakenMarket, cTraderMarket)) && saved.size() == 2;
        }));
    }
}
