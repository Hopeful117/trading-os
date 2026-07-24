package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataStreamProvider;
import com.hope.trading.market_data.exception.EntityNotFoundException;
import com.hope.trading.market_data.helper.MarketSubscriptionKey;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSubscriptionServiceImpl implements MarketSubscriptionService{
    private final MarketDataStreamProvider marketDataStreamProvider;
    private final MarketRepository marketRepository;
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

        log.debug(
                "Subscription count for {} {} is now {}",
                market.getSymbol(),
                request.type(),
                currentCount
        );
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
            log.debug(
                    "No active {} subscription found for market {} with parameters {}",
                    request.type(),
                    marketId,
                    request.parameters()
            );
            return;
        }

        int remainingSubscribers =
                subscriberCount.decrementAndGet();

        if (remainingSubscribers > 0) {
            log.debug(
                    "Subscription count for market {} and stream {} is now {}",
                    marketId,
                    request.type(),
                    remainingSubscribers
            );
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

        if(!market.getMarketState().isTradable()){
            throw new IllegalStateException("Market is not currently tradable"+ market.getSymbol());
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
    }
}
