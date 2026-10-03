package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.repository.MarketRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketHistoryServiceTest {
    @Test
    void selectsProviderFromMarketMetadata() {
        UUID marketId = UUID.randomUUID();
        Market market = new Market();
        market.setMarketId(marketId);
        market.setProvider(MarketProvider.BINANCE);

        MarketRepository repository = mock(MarketRepository.class);
        MarketDataProvider kraken = mock(MarketDataProvider.class);
        MarketDataProvider binance = mock(MarketDataProvider.class);
        when(repository.findById(marketId)).thenReturn(Optional.of(market));
        when(kraken.getName()).thenReturn(MarketProvider.KRAKEN);
        when(binance.getName()).thenReturn(MarketProvider.BINANCE);
        MarketHistorySnapshot expected = new MarketHistorySnapshot(List.of(), List.of());
        when(binance.findOhlcHistorySnapshot(market, OhlcInterval.ONE_MINUTE, 3))
                .thenReturn(expected);

        MarketHistorySnapshot actual = new MarketHistoryService(repository, List.of(kraken, binance))
                .findOhlcHistorySnapshot(marketId, OhlcInterval.ONE_MINUTE, 3);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void unsupportedMarketProviderIsExplicit() {
        UUID marketId = UUID.randomUUID();
        Market market = new Market();
        market.setMarketId(marketId);
        market.setProvider(MarketProvider.COINBASE);
        MarketRepository repository = mock(MarketRepository.class);
        when(repository.findById(marketId)).thenReturn(Optional.of(market));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new MarketHistoryService(repository, List.of())
                                .findOhlcHistorySnapshot(marketId, OhlcInterval.ONE_MINUTE, 3))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
