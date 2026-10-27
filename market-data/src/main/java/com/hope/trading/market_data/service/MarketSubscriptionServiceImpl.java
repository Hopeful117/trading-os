package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerclient.MarketDataStreamProvider;
import com.hope.trading.market_data.exception.EntityNotFoundException;
import com.hope.trading.market_data.helper.MarketSubscriptionKey;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamParameters;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MarketSubscriptionServiceImpl implements MarketSubscriptionService {
    private static final Set<Integer> SUPPORTED_ORDER_BOOK_DEPTHS =
            Set.of(10, 25);

    private final java.util.Map<com.hope.trading.market_data.helper.MarketProvider, MarketDataStreamProvider> streamProviders;
    private final MarketRepository marketRepository;
    private final OrderBookStateService orderBookStateService;
    private final OrderBookEvidenceCaptureService orderBookEvidenceCaptureService;
    private final OrderBookEventPublisher orderBookEventPublisher;
    private final RecentTradesStateService recentTradesStateService;
    private final RecentTradesEventPublisher recentTradesEventPublisher;
    private final ConcurrentHashMap<
            MarketSubscriptionKey,
            AtomicInteger
            > activeSubscriptions = new ConcurrentHashMap<>();

    public MarketSubscriptionServiceImpl(MarketDataStreamProvider marketDataStreamProvider,
                                         MarketRepository marketRepository,
                                         OrderBookStateService orderBookStateService,
                                         OrderBookEvidenceCaptureService orderBookEvidenceCaptureService,
                                         OrderBookEventPublisher orderBookEventPublisher,
                                         RecentTradesStateService recentTradesStateService,
                                         RecentTradesEventPublisher recentTradesEventPublisher) {
        this(java.util.List.of(marketDataStreamProvider), marketRepository, orderBookStateService,
                orderBookEvidenceCaptureService, orderBookEventPublisher, recentTradesStateService,
                recentTradesEventPublisher);
    }

    @Autowired
    public MarketSubscriptionServiceImpl(List<MarketDataStreamProvider> providers,
                                         MarketRepository marketRepository,
                                         OrderBookStateService orderBookStateService,
                                         OrderBookEvidenceCaptureService orderBookEvidenceCaptureService,
                                         OrderBookEventPublisher orderBookEventPublisher,
                                         RecentTradesStateService recentTradesStateService,
                                         RecentTradesEventPublisher recentTradesEventPublisher) {
        this.streamProviders = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                provider -> provider.provider() == null
                        ? com.hope.trading.market_data.helper.MarketProvider.KRAKEN : provider.provider(),
                java.util.function.Function.identity(), (first, ignored) -> first));
        this.marketRepository = marketRepository;
        this.orderBookStateService = orderBookStateService;
        this.orderBookEvidenceCaptureService = orderBookEvidenceCaptureService;
        this.orderBookEventPublisher = orderBookEventPublisher;
        this.recentTradesStateService = recentTradesStateService;
        this.recentTradesEventPublisher = recentTradesEventPublisher;
    }

    @Override
    public synchronized void subscribe(
            UUID marketId,
            MarketStreamRequest request
    ) {
        subscribe(marketId, request, java.time.Duration.ofSeconds(10));
    }

    @Override
    public synchronized void subscribe(UUID marketId, MarketStreamRequest request, java.time.Duration timeout) {
        validateRequest(request);
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new EntityNotFoundException("Market not found: " + marketId));
        validateSubscription(market, request.type());
        subscribeInternal(market, request, timeout);
    }

    @Override
    public synchronized void subscribe(List<UUID> marketIds, MarketStreamRequest request, java.time.Duration timeout) {
        validateRequest(request);

        List<Market> markets = marketRepository.findAllById(marketIds);
        if (markets.size() != marketIds.stream().distinct().count()) {
            throw new EntityNotFoundException("One or more markets were not found");
        }
        markets.forEach(market -> validateSubscription(market, request.type()));

        String previousDiagnosticId = MDC.get("ctraderDiagnosticId");
        String diagnosticId = previousDiagnosticId == null ? UUID.randomUUID().toString() : previousDiagnosticId;
        MDC.put("ctraderDiagnosticId", diagnosticId);
        long startedNanos = System.nanoTime();
        log.info("CTRADER_SUBSCRIPTION_SERVICE_ENTER diagnosticId={} marketIds={} symbols={} provider={} thread={} virtual={}",
                diagnosticId, marketIds, markets.stream().map(Market::getSymbol).toList(),
                markets.stream().map(Market::getProvider).distinct().toList(), Thread.currentThread().getName(),
                Thread.currentThread().isVirtual());
        try {
            markets.stream().collect(java.util.stream.Collectors.groupingBy(Market::getProvider))
                    .values().forEach(group -> subscribeInternal(group, request, timeout));
            log.info("CTRADER_SUBSCRIPTION_SERVICE_EXIT diagnosticId={} marketIds={} durationMs={}",
                    diagnosticId, marketIds, elapsedMillis(startedNanos));
        } catch (RuntimeException exception) {
            log.warn("CTRADER_SUBSCRIPTION_SERVICE_ERROR diagnosticId={} marketIds={} durationMs={} errorType={} message={}",
                    diagnosticId, marketIds, elapsedMillis(startedNanos), exception.getClass().getSimpleName(),
                    exception.getMessage());
            throw exception;
        } finally {
            if (previousDiagnosticId == null) MDC.remove("ctraderDiagnosticId");
            else MDC.put("ctraderDiagnosticId", previousDiagnosticId);
        }
    }

    private void subscribeInternal(Market market, MarketStreamRequest request, java.time.Duration timeout) {
        subscribeInternal(List.of(market), request, timeout);
    }

    private void subscribeInternal(List<Market> markets, MarketStreamRequest request, java.time.Duration timeout) {
        List<Market> newlySubscribed = markets.stream().filter(market -> incrementSubscription(market, request)).toList();
        if (newlySubscribed.isEmpty()) return;

        try {
            providerFor(newlySubscribed.getFirst()).subscribe(newlySubscribed, request).block(timeout);
            newlySubscribed.stream().filter(market -> market.getProvider() == MarketProvider.CTRADER)
                    .forEach(marketRepository::save);
            newlySubscribed.forEach(market -> log.info("Started {} subscription for market {}", request.type(), market.getSymbol()));
        } catch (RuntimeException exception) {
            newlySubscribed.forEach(market -> decrementSubscription(market, request));
            throw exception;
        }

    }

    private boolean incrementSubscription(Market market, MarketStreamRequest request) {
        MarketSubscriptionKey key = new MarketSubscriptionKey(market.getMarketId(), request.type(), request.parameters());
        return activeSubscriptions.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet() == 1;
    }

    private void decrementSubscription(Market market, MarketStreamRequest request) {
        MarketSubscriptionKey key = new MarketSubscriptionKey(market.getMarketId(), request.type(), request.parameters());
        AtomicInteger count = activeSubscriptions.get(key);
        if (count != null && count.decrementAndGet() <= 0) activeSubscriptions.remove(key, count);
    }

    private long elapsedMillis(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    @Override
    public synchronized void unsubscribe(UUID marketId, MarketStreamRequest request) {
        unsubscribe(marketId, request, java.time.Duration.ofSeconds(10));
    }

    @Override
    public synchronized void unsubscribe(UUID marketId, MarketStreamRequest request, java.time.Duration timeout) {

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
            providerFor(market).unsubscribe(
                    List.of(market),
                    request
            ).block(timeout);

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

        if (!market.getMarketState().isTradable() && market.getProvider() != MarketProvider.CTRADER) {
            throw new IllegalStateException("Market is not currently tradable" + market.getSymbol());
        }


        if (streamType == null) {
            throw new IllegalArgumentException(
                    "Market stream type is required"
            );
        }
    }

    private MarketDataStreamProvider providerFor(Market market) {
        MarketDataStreamProvider provider = streamProviders.get(market.getProvider());
        if (provider == null && streamProviders.size() == 1 && market.getProvider()
                == com.hope.trading.market_data.helper.MarketProvider.KRAKEN) {
            provider = streamProviders.values().iterator().next();
        }
        if (provider == null) throw new UnsupportedOperationException("No stream provider registered for " + market.getProvider());
        return provider;
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
                // Ticker subscriptions intentionally accept the optional parameter object.
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
                MarketStreamParameters parameters = request.parameters();
                if (parameters != null
                        && (parameters.interval() != null || parameters.depth() != null)) {
                    throw new IllegalArgumentException("Trades subscription does not accept parameters");
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
            orderBookEvidenceCaptureService.invalidate(key);
        }

        if (request.type() == MarketStreamType.TRADES) {
            recentTradesStateService.clear(marketId);
            recentTradesEventPublisher.clear(marketId);
        }
    }
}
