package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.repository.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSubscriptionServiceImplTest {
    private static final UUID MARKET_ID =
            UUID.fromString("aa53c37c-a454-4f80-9236-13b164af848f");

    @Mock
    private MarketDataStreamProvider streamProvider;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private OrderBookStateService stateService;
    @Mock
    private OrderBookEventPublisher eventPublisher;

    private MarketSubscriptionServiceImpl service;
    private Market market;
    private MarketStreamRequest request;

    @BeforeEach
    void setUp() {
        service = new MarketSubscriptionServiceImpl(
                streamProvider,
                marketRepository,
                stateService,
                eventPublisher
        );
        market = Market.builder()
                .marketId(MARKET_ID)
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/EUR")
                .marketState(MarketState.builder().tradable(true).build())
                .build();
        request = new MarketStreamRequest(
                MarketStreamType.ORDER_BOOK,
                new MarketStreamParameters(null, 10)
        );
    }

    @Test
    void sharesSubscriptionAndCleansStateOnlyAfterLastUnsubscribe() {
        when(marketRepository.findById(MARKET_ID))
                .thenReturn(Optional.of(market));
        when(streamProvider.subscribe(List.of(market), request))
                .thenReturn(Mono.empty());
        when(streamProvider.unsubscribe(List.of(market), request))
                .thenReturn(Mono.empty());

        service.subscribe(MARKET_ID, request);
        service.subscribe(MARKET_ID, request);
        service.unsubscribe(MARKET_ID, request);

        verify(streamProvider, times(1))
                .subscribe(List.of(market), request);
        verify(streamProvider, never())
                .unsubscribe(List.of(market), request);
        verify(stateService, never()).clear(new OrderBookKey(MARKET_ID, 10));

        service.unsubscribe(MARKET_ID, request);

        OrderBookKey key = new OrderBookKey(MARKET_ID, 10);
        verify(streamProvider, times(1))
                .unsubscribe(List.of(market), request);
        verify(stateService).clear(key);
        verify(eventPublisher).clear(key);
    }

    @Test
    void rejectsMissingAndUnsupportedDepths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.subscribe(
                        MARKET_ID,
                        new MarketStreamRequest(
                                MarketStreamType.ORDER_BOOK,
                                null
                        )
                ))
                .withMessage("Order-book subscription requires a depth");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.subscribe(
                        MARKET_ID,
                        new MarketStreamRequest(
                                MarketStreamType.ORDER_BOOK,
                                new MarketStreamParameters(null, 100)
                        )
                ))
                .withMessageContaining("Unsupported order-book depth: 100");

        verify(streamProvider, never())
                .subscribe(List.of(market), request);
    }
}
