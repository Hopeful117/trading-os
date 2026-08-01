package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketPriceSnapshotServiceTest {
    private final MarketRepository repository = mock(MarketRepository.class);
    private final TickerEventPublisher publisher = new TickerEventPublisher(
            repository, mock(PriceObservationRepository.class), Clock.systemUTC());
    private final MarketPriceSnapshotService service =
            new MarketPriceSnapshotService(repository, publisher);

    @Test
    void returnsAvailableMissingAndUnknownPricesInRequestedOrder() {
        UUID availableId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        Market available = market(availableId, "BTC/USD", true);
        Market missing = market(missingId, "ETH/USD", false);
        when(repository.findAllById(List.of(availableId, missingId, unknownId)))
                .thenReturn(List.of(available, missing));
        publisher.publish(new TickerEvent(
                availableId, MarketProvider.KRAKEN, "BTC/USD",
                new BigDecimal("99"), new BigDecimal("101"),
                new BigDecimal("100"), BigDecimal.ONE, Instant.now()
        ));

        var result = service.findSnapshots(List.of(availableId, missingId, unknownId));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).status()).isEqualTo(MarketPriceSnapshotStatus.AVAILABLE);
        assertThat(result.get(0).lastPrice()).isEqualByComparingTo("100");
        assertThat(result.get(1).status()).isEqualTo(MarketPriceSnapshotStatus.PRICE_UNAVAILABLE);
        assertThat(result.get(1).tradable()).isFalse();
        assertThat(result.get(2).status()).isEqualTo(MarketPriceSnapshotStatus.UNKNOWN_MARKET);
    }

    @Test
    void deDuplicatesRequestedMarketIds() {
        UUID marketId = UUID.randomUUID();
        when(repository.findAllById(List.of(marketId))).thenReturn(List.of());

        assertThat(service.findSnapshots(List.of(marketId, marketId))).hasSize(1);
    }

    private Market market(UUID id, String symbol, boolean tradable) {
        return Market.builder()
                .marketId(id)
                .provider(MarketProvider.KRAKEN)
                .symbol(symbol)
                .marketState(MarketState.builder().tradable(tradable).build())
                .build();
    }
}
