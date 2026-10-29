package com.hope.trading.market_data.ctrader;

import com.hope.trading.market_data.brokerclient.MarketDataStreamProvider;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.OhlcEventPublisher;
import com.hope.trading.market_data.service.TickerEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
@Slf4j
public class CTraderDataStreamProvider implements MarketDataStreamProvider {
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final Duration[] RECONNECT_DELAYS = {
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(15)
    };

    private final CTraderOpenApiClient client;
    private final TickerEventPublisher tickerPublisher;
    private final OhlcEventPublisher ohlcPublisher;
    private final Clock clock;
    private final CTraderMarketDataProperties properties;
    private final MarketStateBuilder marketStateBuilder;
    private final Duration[] reconnectDelays;
    private final Map<String, ActiveSubscription> desired = new ConcurrentHashMap<>();
    private final Map<String, OhlcEvent> forming = new ConcurrentHashMap<>();
    private final Set<Long> subscribedSpots = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedTrendbars = ConcurrentHashMap.newKeySet();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final Map<String, Instant> lastProviderEvents = new ConcurrentHashMap<>();
    private volatile CTraderOpenApiClient.CTraderStream stream;
    private volatile boolean intentionallyDisconnected;
    private long connectionGeneration;

    @Autowired
    public CTraderDataStreamProvider(CTraderOpenApiClient client, TickerEventPublisher tickerPublisher,
                                     OhlcEventPublisher ohlcPublisher, Clock clock,
                                     CTraderMarketDataProperties properties, MarketStateBuilder marketStateBuilder) {
        this(client, tickerPublisher, ohlcPublisher, clock, properties, marketStateBuilder, RECONNECT_DELAYS);
    }

    CTraderDataStreamProvider(CTraderOpenApiClient client, TickerEventPublisher tickerPublisher,
                              OhlcEventPublisher ohlcPublisher, Clock clock,
                              CTraderMarketDataProperties properties, MarketStateBuilder marketStateBuilder,
                              Duration[] reconnectDelays) {
        this.client = client;
        this.tickerPublisher = tickerPublisher;
        this.ohlcPublisher = ohlcPublisher;
        this.clock = clock;
        this.properties = properties;
        this.marketStateBuilder = marketStateBuilder;
        this.reconnectDelays = reconnectDelays.clone();
    }

    @Override public MarketProvider provider() { return MarketProvider.CTRADER; }

    @Override
    public synchronized Mono<Void> connect() {
        return connect(true);
    }

    private synchronized Mono<Void> connect(boolean resetReconnectAttempts) {
        intentionallyDisconnected = false;
        if (stream != null) return Mono.empty();
        if (resetReconnectAttempts) reconnectAttempts.set(0);
        long generation = ++connectionGeneration;
        long startedNanos = System.nanoTime();
        String diagnosticId = diagnosticId();
        log.info("CTRADER_OPEN_STREAM_ENTER diagnosticId={} accountId=unknown environment={} generation={} thread={} virtual={}",
                diagnosticId, properties.environment(), generation,
                Thread.currentThread().getName(), Thread.currentThread().isVirtual());
        try {
            stream = client.openStream(event -> handleEvent(generation, event), () -> handleTermination(generation));
            log.info("CTRADER_OPEN_STREAM_EXIT diagnosticId={} accountId=unknown environment={} generation={} durationMs={}",
                    diagnosticId, properties.environment(), generation,
                    elapsedMillis(startedNanos));
        } catch (RuntimeException exception) {
            log.warn("CTRADER_OPEN_STREAM_ERROR diagnosticId={} accountId=unknown environment={} generation={} durationMs={} errorType={} message={}",
                    diagnosticId, properties.environment(), generation,
                    elapsedMillis(startedNanos), exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
        return resubscribe();
    }

    @Override
    public synchronized Mono<Void> disconnect() {
        intentionallyDisconnected = true;
        connectionGeneration++;
        CTraderOpenApiClient.CTraderStream current = stream;
        stream = null;
        if (current != null) current.close();
        forming.clear();
        return Mono.empty();
    }

    @Override
    public synchronized Mono<Void> subscribe(List<Market> markets, MarketStreamRequest request) {
        validate(markets, request);
        return connect().then(Mono.fromRunnable(() -> {
            List<Market> added = markets.stream().filter(Objects::nonNull)
                    .filter(market -> desired.putIfAbsent(key(market, request), new ActiveSubscription(market, request)) == null)
                    .toList();
            try {
                subscribeSpots(added, request);
                added.stream().filter(market -> request.type() == MarketStreamType.OHLC)
                        .forEach(market -> subscribeTrendbar(market, request));
            } catch (RuntimeException exception) {
                rollbackSubscriptions(added, request);
                throw exception;
            }
        }));
    }

    @Override
    public synchronized Mono<Void> unsubscribe(List<Market> markets, MarketStreamRequest request) {
        validate(markets, request);
        return Mono.fromRunnable(() -> markets.stream().filter(Objects::nonNull).forEach(market -> {
            ActiveSubscription removed = desired.remove(key(market, request));
            if (removed != null) forming.remove(key(market, request));
        })).then(Mono.fromRunnable(this::reconcileAfterDemandChange));
    }

    private void subscribeProvider(Market market, MarketStreamRequest request) {
        subscribeSpots(List.of(market), request);
        if (request.type() == MarketStreamType.OHLC) subscribeTrendbar(market, request);
    }

    private void subscribeSpots(long symbolId) {
        subscribeSpots(List.of(symbolId));
    }

    private void subscribeSpots(List<Market> markets, MarketStreamRequest request) {
        if (request.type() != MarketStreamType.TICKER && request.type() != MarketStreamType.OHLC) return;
        subscribeSpots(markets.stream().map(this::symbolId).distinct().toList());
    }

    private synchronized void subscribeSpots(List<Long> symbolIds) {
        List<Long> newSymbolIds = symbolIds.stream().filter(subscribedSpots::add).toList();
        if (newSymbolIds.isEmpty()) return;
        try {
            stream.subscribeSpots(newSymbolIds);
        } catch (RuntimeException exception) {
            subscribedSpots.removeAll(newSymbolIds);
            throw exception;
        }
    }

    private void rollbackSubscriptions(List<Market> markets, MarketStreamRequest request) {
        boolean restartRequired = false;
        for (Market market : markets) {
            desired.remove(key(market, request));
            if ((request.type() == MarketStreamType.TICKER || request.type() == MarketStreamType.OHLC)
                    && noOtherSpotDemand(symbolId(market))) {
                long symbolId = symbolId(market);
                if (subscribedSpots.remove(symbolId)) {
                    restartRequired = true;
                }
            }
            if (request.type() == MarketStreamType.OHLC) {
                subscribedTrendbars.remove(symbolId(market) + ":" + period(request.parameters().interval()));
            }
        }
        if (restartRequired) restartStreamAfterFailedSubscription();
    }

    private void restartStreamAfterFailedSubscription() {
        CTraderOpenApiClient.CTraderStream current = stream;
        if (current == null) return;

        intentionallyDisconnected = true;
        connectionGeneration++;
        stream = null;
        subscribedSpots.clear();
        subscribedTrendbars.clear();
        try {
            current.close();
        } catch (RuntimeException exception) {
            log.warn("CTRADER_FAILED_SUBSCRIPTION_SESSION_CLOSE_ERROR errorType={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
        } finally {
            intentionallyDisconnected = false;
        }

        if (!desired.isEmpty()) {
            try {
                connect(false).subscribe();
            } catch (RuntimeException exception) {
                log.warn("CTRADER_FAILED_SUBSCRIPTION_SESSION_RECONNECT_ERROR errorType={} message={}",
                        exception.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    private void subscribeTrendbar(Market market, MarketStreamRequest request) {
        long symbolId = symbolId(market);
        String trendbarKey = symbolId + ":" + period(request.parameters().interval());
        if (subscribedTrendbars.add(trendbarKey)) stream.subscribeLiveTrendbar(symbolId, period(request.parameters().interval()));
    }

    private boolean noOtherSpotDemand(long symbolId) {
        return desired.values().stream().noneMatch(active ->
                (active.request().type() == MarketStreamType.TICKER || active.request().type() == MarketStreamType.OHLC)
                        && symbolId(active.market()) == symbolId);
    }

    private boolean noOtherTrendbarDemand(String trendbarKey) {
        String[] parts = trendbarKey.split(":", 2);
        long symbolId = Long.parseLong(parts[0]);
        String period = parts[1];
        return desired.values().stream().noneMatch(active ->
                active.request().type() == MarketStreamType.OHLC
                        && symbolId(active.market()) == symbolId
                        && period(active.request().parameters().interval()).equals(period));
    }

    private synchronized void reconcileAfterDemandChange() {
        if (stream == null) return;

        List<String> trendbarsToRemove = subscribedTrendbars.stream()
                .filter(this::noOtherTrendbarDemand)
                .toList();
        List<Long> spotsToRemove = subscribedSpots.stream()
                .filter(this::noOtherSpotDemand)
                .toList();

        try {
            for (String trendbarKey : trendbarsToRemove) {
                String[] parts = trendbarKey.split(":", 2);
                stream.unsubscribeLiveTrendbar(Long.parseLong(parts[0]), parts[1]);
            }
            if (!spotsToRemove.isEmpty()) stream.unsubscribeSpots(spotsToRemove);
            subscribedTrendbars.removeAll(trendbarsToRemove);
            subscribedSpots.removeAll(spotsToRemove);
        } catch (RuntimeException exception) {
            log.warn("CTRADER_UNSUBSCRIBE_RECONCILIATION_ERROR errorType={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            restartStreamAfterFailedSubscription();
            throw exception;
        }
    }

    private synchronized void handleEvent(long generation, CTraderOpenApiClient.CTraderStreamEvent event) {
        if (generation != connectionGeneration) return;
        if (event instanceof CTraderOpenApiClient.CTraderSpotEvent spot) handleSpot(spot);
        if (event instanceof CTraderOpenApiClient.CTraderLiveTrendbarEvent trendbar) handleTrendbar(trendbar);
    }

    private void handleSpot(CTraderOpenApiClient.CTraderSpotEvent spot) {
        if (spot.bid() == null || spot.ask() == null) return;
        desired.values().stream().filter(active -> active.request().type() == MarketStreamType.TICKER
                        && symbolId(active.market()) == spot.symbolId()).forEach(active -> {
            Instant received = clock.instant();
            lastProviderEvents.put(key(active.market(), active.request()), received);
            active.market().setMarketState(MarketState.builder()
                    .tradingStatus(com.hope.trading.market_data.helper.TradingStatus.OPEN)
                    .tradable(true)
                    .lastUpdated(received)
                    .build());
            tickerPublisher.publish(new TickerEvent(active.market().getMarketId(), MarketProvider.CTRADER,
                    active.market().getSymbol(), decode(spot.bid(), active.market()), decode(spot.ask(), active.market()),
                    null, null, spot.occurredAt(),
                    new CTraderInstrumentIdentity(spot.symbolId(), active.market().getSymbol(), properties.environment().name()).provenance(),
                    received, Long.toString(spot.symbolId())));
            log.info("CTRADER_SPOT_EVENT_NORMALIZED diagnosticId={} marketId={} symbol={} symbolId={} hasBid=true hasAsk=true bid={} ask={} marketTradable={}",
                    diagnosticId(), active.market().getMarketId(), active.market().getSymbol(), spot.symbolId(),
                    decode(spot.bid(), active.market()), decode(spot.ask(), active.market()),
                    active.market().getMarketState() != null && active.market().getMarketState().isTradable());
        });
    }

    private void handleTrendbar(CTraderOpenApiClient.CTraderLiveTrendbarEvent event) {
        desired.values().stream().filter(active -> active.request().type() == MarketStreamType.OHLC
                        && symbolId(active.market()) == event.symbolId()
                        && period(active.request().parameters().interval()).equals(event.period()))
                .forEach(active -> publishTrendbar(active, event));
    }

    private void publishTrendbar(ActiveSubscription active, CTraderOpenApiClient.CTraderLiveTrendbarEvent event) {
        lastProviderEvents.put(key(active.market(), active.request()), clock.instant());
        OhlcInterval interval = OhlcInterval.fromMinutes(active.request().parameters().interval());
        Instant open = Instant.ofEpochSecond(event.trendbar().timestamp());
        Instant close = open.plus(interval.getDuration());
        String key = key(active.market(), active.request());
        OhlcEvent previous = forming.get(key);
        if (previous != null && open.isBefore(previous.openTime())) return;
        if (previous != null && open.equals(previous.openTime())
                && sameBar(previous, event, active.market())) return;
        if (previous != null && open.isAfter(previous.openTime()) && !previous.closed() && previous.close() != null) {
            ohlcPublisher.publish(new OhlcEvent(previous.marketId(), previous.provider(), previous.symbol(),
                    previous.interval(), previous.openTime(), previous.closeTime(), previous.open(), previous.high(),
                    previous.low(), previous.close(), previous.volume(), previous.vwap(), previous.trades(), true,
                    previous.occurredAt(), false, previous.sourceId(), previous.fetchedAt(), previous.providerSymbol()));
        }
        OhlcEvent current = toEvent(active.market(), interval, event, open, close, false);
        forming.put(key, current);
        ohlcPublisher.publish(current);
    }

    private boolean sameBar(OhlcEvent previous, CTraderOpenApiClient.CTraderLiveTrendbarEvent event, Market market) {
        CTraderOpenApiClient.CTraderTrendbar bar = event.trendbar();
        BigDecimal low = decode(bar.low(), market);
        return previous.open().compareTo(low.add(decode(bar.open(), market))) == 0
                && previous.high().compareTo(low.add(decode(bar.high(), market))) == 0
                && previous.low().compareTo(low) == 0
                && samePrice(previous.close(), effectiveClose(event, low, market))
                && Objects.equals(previous.volume(), bar.volume() > 0 ? BigDecimal.valueOf(bar.volume()) : null);
    }

    private boolean samePrice(BigDecimal previous, BigDecimal current) {
        return previous == null ? current == null : current != null && previous.compareTo(current) == 0;
    }

    private OhlcEvent toEvent(Market market, OhlcInterval interval,
                              CTraderOpenApiClient.CTraderLiveTrendbarEvent event,
                              Instant open, Instant close, boolean closed) {
        CTraderOpenApiClient.CTraderTrendbar bar = event.trendbar();
        BigDecimal low = decode(bar.low(), market);
        Instant received = clock.instant();
        String source = new CTraderInstrumentIdentity(event.symbolId(), market.getSymbol(), properties.environment().name()).provenance();
        BigDecimal closePrice = effectiveClose(event, low, market);
        return new OhlcEvent(market.getMarketId(), MarketProvider.CTRADER, market.getSymbol(), interval, open, close,
                low.add(decode(bar.open(), market)), low.add(decode(bar.high(), market)), low,
                closePrice, bar.volume() > 0 ? BigDecimal.valueOf(bar.volume()) : null,
                null, null, closed, event.occurredAt(), false, source, received, Long.toString(event.symbolId()));
    }

    private synchronized void handleTermination(long generation) {
        if (generation != connectionGeneration) return;
        stream = null;
        subscribedSpots.clear();
        subscribedTrendbars.clear();
        markUnavailable();
        if (intentionallyDisconnected || desired.isEmpty()) return;
        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) return;
        reactor.core.publisher.Mono.delay(reconnectDelays[attempt - 1]).subscribe(ignored -> {
            synchronized (this) {
                if (!intentionallyDisconnected && stream == null) connect(false).subscribe();
            }
        });
    }

    @Scheduled(fixedDelayString = "${ctrader.market-data.freshness-check-delay:PT5S}")
    public void refreshScheduledFreshness() {
        refreshFreshness(Duration.ofSeconds(30));
    }

    /** Marks provider silence as unavailable without asserting a local trading schedule. */
    public synchronized void refreshFreshness(Duration silenceAfter) {
        Instant boundary = clock.instant().minus(silenceAfter);
        desired.values().forEach(active -> {
            Instant last = lastProviderEvents.get(key(active.market(), active.request()));
            if (last == null || last.isBefore(boundary)) {
                active.market().setMarketState(MarketState.builder()
                        .tradingStatus(com.hope.trading.market_data.helper.TradingStatus.CLOSED)
                        .tradable(false)
                        .closureReason(com.hope.trading.market_data.helper.MarketClosureReason.PROVIDER_UNAVAILABLE)
                        .lastUpdated(clock.instant()).build());
            }
        });
    }

    private void markUnavailable() {
        desired.values().forEach(active -> active.market().setMarketState(MarketState.builder()
                .tradingStatus(com.hope.trading.market_data.helper.TradingStatus.CLOSED)
                .tradable(false)
                .closureReason(com.hope.trading.market_data.helper.MarketClosureReason.PROVIDER_UNAVAILABLE)
                .lastUpdated(clock.instant()).build()));
    }

    public boolean isFresh(Market market, MarketStreamRequest request, Duration silenceAfter) {
        Instant last = lastProviderEvents.get(key(market, request));
        return last != null && !last.isBefore(clock.instant().minus(silenceAfter));
    }

    private Mono<Void> resubscribe() {
        desired.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(ActiveSubscription::request))
                .forEach((request, subscriptions) -> {
                    List<Market> markets = subscriptions.stream().map(ActiveSubscription::market).toList();
                    subscribeSpots(markets, request);
                    if (request.type() == MarketStreamType.OHLC) {
                        markets.forEach(market -> subscribeTrendbar(market, request));
                    }
                });
        return Mono.empty();
    }

    private BigDecimal decode(Long value, Market market) {
        if (value == null) return null;
        Integer precision = market.getMarketConstraints() == null
                ? null : market.getMarketConstraints().pricePrecision();
        return com.hope.trading.ctrader.openapi.codec.CTraderPriceDecoder.decode(
                value, precision == null ? 5 : precision);
    }

    private BigDecimal effectiveClose(CTraderOpenApiClient.CTraderLiveTrendbarEvent event,
                                      BigDecimal low, Market market) {
        return event.closeAvailable()
                ? low.add(decode(event.trendbar().close(), market))
                : decode(event.spotBid(), market);
    }

    private long symbolId(Market market) {
        if (market == null || market.getProvider() != MarketProvider.CTRADER
                || market.getProviderSymbol() == null || !market.getProviderSymbol().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("cTrader market requires a numeric provider symbol");
        }
        return Long.parseLong(market.getProviderSymbol());
    }

    private String diagnosticId() {
        String value = MDC.get("ctraderDiagnosticId");
        return value == null ? "none" : value;
    }

    private long elapsedMillis(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String key(Market market, MarketStreamRequest request) {
        return market.getProvider() + ":" + market.getProviderSymbol() + ":" + request.type() + ":"
                + (request.parameters() == null ? "" : request.parameters().interval());
    }

    private String period(Integer minutes) {
        return switch (OhlcInterval.fromMinutes(minutes)) {
            case ONE_MINUTE -> "M1"; case FIVE_MINUTES -> "M5"; case FIFTEEN_MINUTES -> "M15";
            case THIRTY_MINUTES -> "M30"; case ONE_HOUR -> "H1"; case FOUR_HOURS -> "H4";
            case TWELVE_HOURS -> "H12"; case ONE_DAY -> "D1"; case ONE_WEEK -> "W1";
        };
    }

    private void validate(List<Market> markets, MarketStreamRequest request) {
        if (markets == null || request == null || request.type() == null) throw new IllegalArgumentException("cTrader stream request is required");
        if (request.type() != MarketStreamType.TICKER && request.type() != MarketStreamType.OHLC) {
            throw new UnsupportedOperationException("cTrader supports only ticker and OHLC streams");
        }
        if (request.type() == MarketStreamType.OHLC && (request.parameters() == null || request.parameters().interval() == null)) {
            throw new IllegalArgumentException("cTrader OHLC stream requires an interval");
        }
        markets.stream().filter(Objects::nonNull).forEach(this::symbolId);
    }

    private record ActiveSubscription(Market market, MarketStreamRequest request) { }
}
