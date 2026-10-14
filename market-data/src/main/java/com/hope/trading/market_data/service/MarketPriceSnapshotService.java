package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshot;
import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.model.MarketStreamRequest;
import com.hope.trading.market_data.model.MarketStreamType;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Clock;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class MarketPriceSnapshotService {
    private static final Duration MAX_ACQUISITION_TIMEOUT = Duration.ofSeconds(18);
    private final MarketRepository marketRepository;
    private final TickerEventPublisher tickerEventPublisher;
    private final Map<MarketProvider, MarketDataProvider> providers;
    private final Clock clock;
    private final Duration staleAfter;
    private final MarketSubscriptionService subscriptionService;
    private final ConcurrentMap<AcquisitionKey, CompletableFuture<Acquisition>> inFlightAcquisitions =
            new ConcurrentHashMap<>();

    public MarketPriceSnapshotService(
            MarketRepository marketRepository,
            TickerEventPublisher tickerEventPublisher,
            List<MarketDataProvider> providers,
            Clock clock,
            @Value("${market-data.snapshot.stale-after:30s}") Duration staleAfter) {
        this(marketRepository, tickerEventPublisher, providers, clock, staleAfter, null);
    }

    @Autowired
    public MarketPriceSnapshotService(
            MarketRepository marketRepository,
            TickerEventPublisher tickerEventPublisher,
            List<MarketDataProvider> providers,
            Clock clock,
            @Value("${market-data.snapshot.stale-after:30s}") Duration staleAfter,
            MarketSubscriptionService subscriptionService) {
        this.marketRepository = marketRepository;
        this.tickerEventPublisher = tickerEventPublisher;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                MarketDataProvider::getName,
                Function.identity()
        ));
        this.clock = clock;
        this.staleAfter = staleAfter;
        this.subscriptionService = subscriptionService;
    }

    public List<MarketPriceSnapshot> findSnapshots(List<UUID> marketIds) {
        return findSnapshots(new com.hope.trading.market_data.dto.MarketPriceSnapshotRequest(marketIds));
    }

    public List<MarketPriceSnapshot> findSnapshots(
            com.hope.trading.market_data.dto.MarketPriceSnapshotRequest request) {
        Duration freshness = request.maxObservationAge() == null ? staleAfter : request.maxObservationAge();
        Duration timeout = request.acquisitionTimeout() == null ? staleAfter : request.acquisitionTimeout();
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Acquisition timeout must be positive");
        }
        timeout = timeout.compareTo(MAX_ACQUISITION_TIMEOUT) > 0 ? MAX_ACQUISITION_TIMEOUT : timeout;
        Duration boundedTimeout = timeout;
        boolean structured = request.maxObservationAge() != null || request.acquisitionMode() != null;
        boolean confirm = "CONFIRM_OR_ACQUIRE".equalsIgnoreCase(request.acquisitionMode());
        List<UUID> requestedIds = request.marketIds().stream().distinct().toList();
        Map<UUID, Market> markets = marketRepository.findAllById(requestedIds)
                .stream()
                .collect(Collectors.toMap(Market::getMarketId, Function.identity()));

        return requestedIds.stream()
                .map(marketId -> toSnapshot(marketId, markets.get(marketId), freshness, boundedTimeout, confirm, structured))
                .toList();
    }

    private MarketPriceSnapshot toSnapshot(UUID marketId, Market market, Duration freshness,
                                           Duration timeout, boolean confirm, boolean structured) {
        if (market == null) {
            return new MarketPriceSnapshot(
                    marketId, null, null, null, null, false, null,
                    MarketPriceSnapshotStatus.UNKNOWN_MARKET, null, null, clock.instant()
            );
        }

        TickerEvent ticker = tickerEventPublisher.latestByMarketId(marketId).orElse(null);
        boolean tradable = market.getMarketState() != null
                && market.getMarketState().isTradable();

        if (!confirm && ticker != null && isFresh(ticker, freshness)) {
            return toSnapshot(marketId, market, tradable, ticker, MarketPriceSnapshotStatus.FRESH);
        }

        Acquisition acquisition = acquireCurrentState(market, freshness, timeout, confirm, ticker);
        Optional<TickerEvent> refreshed = acquisition.event();
        if (refreshed.isPresent() && isFresh(refreshed.get(), freshness)) {
            return toSnapshot(
                    marketId,
                    market,
                    tradable,
                    refreshed.get(),
                    MarketPriceSnapshotStatus.FRESH
            );
        }

        if (structured && acquisition.timedOut()) {
            return new MarketPriceSnapshot(
                    marketId, market.getSymbol(), null, null, null, tradable, null,
                    MarketPriceSnapshotStatus.TIMEOUT, null, null, clock.instant()
            );
        }

        if (ticker != null && hasUsablePrice(ticker)) {
            return toSnapshot(marketId, market, tradable, ticker,
                    structured && acquisition.timedOut() ? MarketPriceSnapshotStatus.TIMEOUT
                            : structured && acquisition.providerResponded()
                            ? MarketPriceSnapshotStatus.STALE_PROVIDER : MarketPriceSnapshotStatus.STALE);
        }

        if (refreshed.isPresent() && hasUsablePrice(refreshed.get())) {
            return toSnapshot(
                    marketId,
                    market,
                    tradable,
                    refreshed.get(),
                    structured && acquisition.timedOut() ? MarketPriceSnapshotStatus.TIMEOUT
                            : structured ? MarketPriceSnapshotStatus.STALE_PROVIDER : MarketPriceSnapshotStatus.STALE
            );
        }

        if (ticker == null || !hasUsablePrice(ticker)) {
            return new MarketPriceSnapshot(
                    marketId, market.getSymbol(), null, null, null, tradable, null,
                    structured && acquisition.timedOut() ? MarketPriceSnapshotStatus.TIMEOUT
                            : MarketPriceSnapshotStatus.UNAVAILABLE, null, null, clock.instant()
            );
        }
        return toSnapshot(marketId, market, tradable, ticker, MarketPriceSnapshotStatus.STALE);
    }

    private MarketPriceSnapshot toSnapshot(
            UUID marketId,
            Market market,
            boolean tradable,
            TickerEvent ticker,
            MarketPriceSnapshotStatus status
    ) {
        UUID sourceIdentity = UUID.nameUUIDFromBytes((
                marketId + "|" + ticker.provider() + "|" + ticker.symbol() + "|"
                        + ticker.bid() + "|" + ticker.ask() + "|" + ticker.last() + "|"
                        + ticker.volume() + "|" + ticker.occurredAt())
                .getBytes(StandardCharsets.UTF_8));
        long sourceVersion = sourceIdentity.getMostSignificantBits() & Long.MAX_VALUE;
        return new MarketPriceSnapshot(
                marketId,
                market.getSymbol(),
                ticker.last(),
                ticker.bid(),
                ticker.ask(),
                tradable,
                ticker.occurredAt(),
                status,
                "ticker:" + sourceIdentity,
                sourceVersion == 0 ? 1 : sourceVersion,
                clock.instant()
        );
    }

    private Acquisition acquireCurrentState(Market market, Duration freshness, Duration timeout,
                                            boolean confirm, TickerEvent previous) {
        MarketDataProvider provider = providers.get(market.getProvider());
        if (provider == null) {
            log.warn("No MarketDataProvider registered for provider={}", market.getProvider());
            return new Acquisition(Optional.empty(), false, false);
        }
        while (true) {
            TickerEvent cached = tickerEventPublisher.latestByMarketId(market.getMarketId()).orElse(null);
            if (!confirm && cached != null && isFresh(cached, freshness)) {
                return new Acquisition(Optional.of(cached), false, false);
            }

            AcquisitionKey key = new AcquisitionKey(market.getMarketId(), freshness, confirm);
            CompletableFuture<Acquisition> claim = new CompletableFuture<>();
            CompletableFuture<Acquisition> existing = inFlightAcquisitions.putIfAbsent(key, claim);
            if (existing == null) {
                try {
                    long deadlineNanos = System.nanoTime() + timeout.toNanos();
                    ProviderAcquisition providerResult = acquireFromProvider(provider, market,
                            remaining(deadlineNanos));
                    if (providerResult.timedOut()) {
                        Acquisition result = new Acquisition(Optional.empty(), true, false);
                        claim.complete(result);
                        return result;
                    }
                    Optional<TickerEvent> acquired = providerResult.event();
                    acquired.ifPresent(tickerEventPublisher::recordCurrentState);
                    Acquisition result = acquireAlternateIfNeeded(market, acquired, freshness,
                            remaining(deadlineNanos), previous);
                    claim.complete(result);
                    return result;
                } catch (RuntimeException exception) {
                    log.warn(
                            "Current snapshot acquisition failed marketId={} provider={} message={}",
                            market.getMarketId(),
                            market.getProvider(),
                            exception.getMessage()
                    );
                    claim.complete(new Acquisition(Optional.empty(), false, false));
                    return new Acquisition(Optional.empty(), false, false);
                } finally {
                    inFlightAcquisitions.remove(key, claim);
                }
            }
            try {
                return existing.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new Acquisition(Optional.empty(), true, false);
            } catch (TimeoutException exception) {
                return new Acquisition(Optional.empty(), true, false);
            } catch (ExecutionException | CompletionException exception) {
                log.warn(
                        "Waiting snapshot acquisition failed marketId={} provider={} message={}",
                        market.getMarketId(),
                        market.getProvider(),
                        exception.getMessage()
                );
                return new Acquisition(Optional.empty(), false, false);
            }
        }
    }

    private Acquisition acquireAlternateIfNeeded(Market market, Optional<TickerEvent> acquired,
                                                  Duration freshness, Duration timeout,
                                                  TickerEvent previous) {
        if (timeout.isZero() || timeout.isNegative()) {
            return new Acquisition(acquired, true, acquired.isPresent());
        }
        if (acquired.isPresent() && isFresh(acquired.get(), freshness)) {
            return new Acquisition(acquired, false, true);
        }
        if (subscriptionService == null || market.getMarketState() == null
                || !market.getMarketState().isTradable()) {
            return new Acquisition(acquired, false, acquired.isPresent());
        }
        MarketStreamRequest request = new MarketStreamRequest(MarketStreamType.TICKER, null);
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        try {
            subscriptionService.subscribe(market.getMarketId(), request, remaining(deadlineNanos));
            Duration streamTimeout = remaining(deadlineNanos);
            if (streamTimeout.isZero() || streamTimeout.isNegative()) {
                return new Acquisition(acquired, true, acquired.isPresent());
            }
            Optional<TickerEvent> live = tickerEventPublisher.streamBySymbol(market.getSymbol())
                    .filter(event -> event.marketId() != null && event.marketId().equals(market.getMarketId()))
                    .filter(event -> previous == null || event.occurredAt() == null
                            || !event.occurredAt().isBefore(previous.occurredAt()))
                    .filter(event -> isFresh(event, freshness))
                    .next()
                    .timeout(streamTimeout)
                    .blockOptional();
            return live.map(event -> new Acquisition(Optional.of(event), false, true))
                    .orElseGet(() -> new Acquisition(acquired, true, acquired.isPresent()));
        } catch (RuntimeException exception) {
            log.warn("Bounded ticker alternate failed marketId={} message={}",
                    market.getMarketId(), exception.getMessage());
            return new Acquisition(acquired, true, acquired.isPresent());
        } finally {
            try {
                subscriptionService.unsubscribe(market.getMarketId(), request, remaining(deadlineNanos));
            } catch (RuntimeException cleanupFailure) {
                log.warn("Unable to release temporary ticker subscription marketId={} message={}",
                        market.getMarketId(), cleanupFailure.getMessage());
            }
        }
    }

    private ProviderAcquisition acquireFromProvider(MarketDataProvider provider, Market market,
                                                     Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            return new ProviderAcquisition(Optional.empty(), true);
        }
        try {
            CompletableFuture<Optional<TickerEvent>> future = CompletableFuture
                    .supplyAsync(() -> provider.acquireCurrentSnapshot(market));
            Optional<TickerEvent> event;
            try {
                event = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | TimeoutException exception) {
                future.cancel(true);
                throw exception;
            }
            return new ProviderAcquisition(event, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProviderAcquisition(Optional.empty(), true);
        } catch (TimeoutException exception) {
            return new ProviderAcquisition(Optional.empty(), true);
        } catch (ExecutionException | CompletionException exception) {
            log.warn(
                    "Current snapshot acquisition failed marketId={} provider={} message={}",
                    market.getMarketId(),
                    market.getProvider(),
                    exception.getMessage()
            );
            return new ProviderAcquisition(Optional.empty(), false);
        } catch (RuntimeException exception) {
            log.warn(
                    "Current snapshot acquisition failed marketId={} provider={} message={}",
                    market.getMarketId(),
                    market.getProvider(),
                    exception.getMessage()
            );
            return new ProviderAcquisition(Optional.empty(), false);
        }
    }

    private Duration remaining(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos <= 0 ? Duration.ZERO : Duration.ofNanos(remainingNanos);
    }

    private boolean isFresh(TickerEvent ticker, Duration freshness) {
        java.time.Instant now = clock.instant();
        return ticker != null
                && ticker.occurredAt() != null
                && !ticker.occurredAt().isAfter(now)
                && !ticker.occurredAt().isBefore(now.minus(freshness))
                && hasUsablePrice(ticker);
    }

    private boolean hasUsablePrice(TickerEvent ticker) {
        return ticker != null && ticker.bid() != null && ticker.ask() != null;
    }

    private record Acquisition(Optional<TickerEvent> event, boolean timedOut, boolean providerResponded) { }

    private record ProviderAcquisition(Optional<TickerEvent> event, boolean timedOut) { }

    private record AcquisitionKey(UUID marketId, Duration freshness, boolean confirm) { }
}
