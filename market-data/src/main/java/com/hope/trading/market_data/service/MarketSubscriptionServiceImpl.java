package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.exception.EntityNotFoundException;
import com.hope.trading.market_data.helper.MarketSubscriptionKey;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSubscriptionServiceImpl implements MarketSubscriptionService {
    private static final Set<Integer> SUPPORTED_ORDER_BOOK_DEPTHS =
            Set.of(10, 25);

    private final MarketDataStreamProvider marketDataStreamProvider;
    private final MarketRepository marketRepository;
    private final OrderBookStateService orderBookStateService;
    private final OrderBookEventPublisher orderBookEventPublisher;
    private final RecentTradesStateService recentTradesStateService;
    private final RecentTradesEventPublisher recentTradesEventPublisher;
    private final ConcurrentHashMap<
            MarketSubscriptionKey,
            AtomicInteger
            > activeSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void subscribe(
            UUID marketId,
            MarketStreamRequest request
    ) {
        validateRequest(request);


        Market market = marketRepository.findById(marketId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Market not found: " + marketId
                        )
                );

        validateSubscription(market, request.type());

        MarketSubscriptionKey key =
                new MarketSubscriptionKey(
                        marketId,
                        request.type(),
                        request.parameters()
                );

        AtomicInteger subscriberCount =
                activeSubscriptions.computeIfAbsent(
                        key,
                        ignored -> new AtomicInteger(0)
                );

        int currentCount =
                subscriberCount.incrementAndGet();

        if (currentCount == 1) {
            try {
                marketDataStreamProvider
                        .subscribe(
                                List.of(market),
                                request
                        )
                        .block(java.time.Duration.ofSeconds(10));

                log.info(
                        "Started {} subscription for market {}, count {}",
                        request.type(),
                        market.getSymbol(),
                        currentCount
                );

            } catch (RuntimeException exception) {
                activeSubscriptions.remove(key);
                throw exception;
            }
        }

    }

    @Override
    public void unsubscribe(UUID marketId, MarketStreamRequest request) {

        validateRequest(request);

        MarketSubscriptionKey key =
                new MarketSubscriptionKey(
                        marketId,
                        request.type(),
                        request.parameters()
                );

        AtomicInteger subscriberCount =
                activeSubscriptions.get(key);

        if (subscriberCount == null) {
            return;
        }

        int remainingSubscribers =
                subscriberCount.decrementAndGet();

        if (remainingSubscribers > 0) {
            return;
        }


        if (remainingSubscribers < 0) {
            activeSubscriptions.remove(key, subscriberCount);

            log.warn(
                    "Subscription count became negative for market {} and stream {}",
                    marketId,
                    request.type()
            );

            return;
        }

        Market market = marketRepository.findById(marketId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Market not found: " + marketId
                        )
                );

        try {
            marketDataStreamProvider.unsubscribe(
                    List.of(market),
                    request
            ).block(java.time.Duration.ofSeconds(10));

            activeSubscriptions.remove(key, subscriberCount);
            clearStreamState(marketId, request);

            log.info(
                    "Stopped {} subscription for market {}",
                    request.type(),
                    market.getSymbol()
            );

        } catch (RuntimeException exception) {
            subscriberCount.incrementAndGet();
            throw exception;
        }

    }

    private void validateSubscription(
            Market market,
            MarketStreamType streamType
    ) {
        if (market.getMarketState() == null) {
            throw new IllegalStateException(
                    "Market state is unavailable for "
                            + market.getSymbol()
            );
        }

        if (!market.getMarketState().isTradable()) {
            throw new IllegalStateException("Market is not currently tradable" + market.getSymbol());
        }


        if (streamType == null) {
            throw new IllegalArgumentException(
                    "Market stream type is required"
            );
        }
    }

    private void validateRequest(MarketStreamRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Market stream request is required"
            );
        }

        if (request.type() == null) {
            throw new IllegalArgumentException(
                    "Market stream type is required"
            );
        }
        switch (request.type()) {
            case TICKER -> {

            }

            case OHLC -> {
                if (request.parameters() == null
                        || request.parameters().interval() == null) {
                    throw new IllegalArgumentException(
                            "OHLC subscription requires an interval"
                    );
                }

                OhlcInterval.fromMinutes(
                        request.parameters().interval()
                );
            }
            case ORDER_BOOK -> {
                if (request.parameters() == null
                        || request.parameters().depth() == null) {
                    throw new IllegalArgumentException(
                            "Order-book subscription requires a depth"
                    );
                }

                if (!SUPPORTED_ORDER_BOOK_DEPTHS.contains(
                        request.parameters().depth()
                )) {
                    throw new IllegalArgumentException(
                            "Unsupported order-book depth: "
                                    + request.parameters().depth()
                                    + ". Supported depths are "
                                    + SUPPORTED_ORDER_BOOK_DEPTHS
                    );
                }
            }
            case TRADES -> {
                if (request.parameters() != null
                        && (request.parameters().interval() != null
                        || request.parameters().depth() != null)) {
                    throw new IllegalArgumentException(
                            "Trades subscription does not accept parameters"
                    );
                }
            }
        }
    }

    private void clearStreamState(
            UUID marketId,
            MarketStreamRequest request
    ) {
        if (request.type() == MarketStreamType.ORDER_BOOK) {
            OrderBookKey key = new OrderBookKey(
                    marketId,
                    request.parameters().depth()
            );
            orderBookStateService.clear(key);
            orderBookEventPublisher.clear(key);
        }

        if (request.type() == MarketStreamType.TRADES) {
            recentTradesStateService.clear(marketId);
            recentTradesEventPublisher.clear(marketId);
        }
    }
}
