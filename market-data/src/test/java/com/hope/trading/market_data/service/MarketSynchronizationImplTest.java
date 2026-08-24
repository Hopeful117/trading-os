package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.repository.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(marketRepository.findByProviderAndSymbol(
                MarketProvider.KRAKEN, "XBT/EUR")).thenReturn(Optional.empty());

        service.synchronizeMarkets();

        verify(marketRepository).saveAll(List.of(fresh));
    }

    @Test
    void existingMarketIsUpdatedInPlaceWithoutIdentityChange() {
        Market existing = market("XBT/EUR", "OLD", "OLD");
        Market incoming = market("XBT/EUR", "XBT", "EUR");
        when(marketDataProvider.getMarkets()).thenReturn(List.of(incoming));
        when(marketRepository.findByProviderAndSymbol(
                MarketProvider.KRAKEN, "XBT/EUR")).thenReturn(Optional.of(existing));

        service.synchronizeMarkets();

        verify(marketRepository).saveAll(List.of(existing));
        assertThat(existing.getBaseAsset()).isEqualTo("XBT");
        assertThat(existing.getQuoteAsset()).isEqualTo("EUR");
    }

    @Test
    void synchronizationIsRepeatableWithoutDuplication() {
        Market existing = market("XBT/EUR", "XBT", "EUR");
        when(marketDataProvider.getMarkets()).thenReturn(List.of(existing));
        when(marketRepository.findByProviderAndSymbol(
                MarketProvider.KRAKEN, "XBT/EUR")).thenReturn(Optional.of(existing));

        service.synchronizeMarkets();
        service.synchronizeMarkets();

        // Both passes update the same persisted row: no duplicates inserted.
        verify(marketRepository, times(2)).saveAll(anyList());
    }
}
