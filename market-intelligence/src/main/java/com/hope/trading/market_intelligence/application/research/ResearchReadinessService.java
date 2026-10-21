package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookReplayClient;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookReplayIdentity;
import com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient;
import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ResearchReadinessService {
    private static final Logger log = LoggerFactory.getLogger(ResearchReadinessService.class);
    private static final String PROJECTION_VERSION = "feature-projection-v1";
    private static final int REPLAY_DEPTH = 25;
    private final ResearchLifecycleProperties properties;
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final HistoricalOhlcClient historicalOhlc;
    private final ArtifactPersistencePort artifacts;
    private final OrderBookReplayClient replay;
    private final HistoricalFeeScheduleClient fees;
    private final Clock clock;
    private volatile CachedReadiness cachedReadiness;

    public ResearchReadinessService(
            ResearchLifecycleProperties properties,
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            HistoricalOhlcClient historicalOhlc,
            Clock clock
    ) {
        this(properties, snapshots, outcomes, historicalOhlc, null, null, null, clock);
    }

    @Autowired
    public ResearchReadinessService(
            ResearchLifecycleProperties properties,
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            HistoricalOhlcClient historicalOhlc,
            ArtifactPersistencePort artifacts,
            OrderBookReplayClient replay,
            HistoricalFeeScheduleClient fees,
            Clock clock
    ) {
        this.properties = properties;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.historicalOhlc = historicalOhlc;
        this.artifacts = artifacts;
        this.replay = replay;
        this.fees = fees;
        this.clock = clock;
    }

    public ResearchReadiness current() {
        properties.validate();
        Instant now = clock.instant();
        CachedReadiness cached = cachedReadiness;
        if (cached != null && now.isBefore(cached.expiresAt())) {
            log.debug("Research readiness cache hit markets={} remainingCacheMs={}",
                    properties.getMarketIds().size(), Duration.between(now, cached.expiresAt()).toMillis());
            return cached.value();
        }
        synchronized (this) {
            now = clock.instant();
            cached = cachedReadiness;
            if (cached != null && now.isBefore(cached.expiresAt())) {
                log.debug("Research readiness cache hit after lock markets={} remainingCacheMs={}",
                        properties.getMarketIds().size(), Duration.between(now, cached.expiresAt()).toMillis());
                return cached.value();
            }
            log.debug("Research readiness cache miss markets={}", properties.getMarketIds().size());
            ResearchReadiness computed = computeCurrent(now);
            cachedReadiness = new CachedReadiness(
                    computed, clock.instant().plus(properties.getReadinessCacheDuration()));
            return computed;
        }
    }

    private ResearchReadiness computeCurrent(Instant now) {
        long computationStartedAt = System.nanoTime();
        Set<UUID> markets = properties.getMarketIds();
        long snapshotLoadStartedAt = System.nanoTime();
        List<FeatureSnapshot> values = markets.isEmpty()
                ? List.of()
                : snapshots.findByPopulation(
                         markets, Instant.EPOCH, now.plusSeconds(1), PROJECTION_VERSION,
                         EvidenceAcquisitionPolicy.STRICT_OBSERVED.name());
        long snapshotLoadDurationMs = elapsedMillis(snapshotLoadStartedAt);
        Set<java.time.Duration> configuredHorizons = Set.copyOf(properties.getOutcomeHorizons());
        long outcomeLoadStartedAt = System.nanoTime();
        List<ObservedMarketOutcome> observedOutcomes = outcomes.findByMarkets(markets).stream()
                .filter(outcome -> configuredHorizons.contains(outcome.horizon().value()))
                .toList();
        long outcomeLoadDurationMs = elapsedMillis(outcomeLoadStartedAt);

        long latestEvidenceStartedAt = System.nanoTime();
        Map<UUID, Instant> latestEvidence = latestEvidence(now, markets);
        long latestEvidenceDurationMs = elapsedMillis(latestEvidenceStartedAt);
        Map<UUID, Long> snapshotsByMarket = values.stream()
                .collect(Collectors.groupingBy(FeatureSnapshot::marketId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> outcomeCounts = observedOutcomes.stream()
                .collect(Collectors.groupingBy(value -> value.horizon().value() + ":" + value.status(),
                        LinkedHashMap::new, Collectors.counting()));
        long available = observedOutcomes.stream()
                .filter(value -> value.status() == ObservedMarketOutcomeStatus.AVAILABLE)
                .count();
        long pending = values.stream()
                .mapToLong(snapshot -> properties.getOutcomeHorizons().stream()
                        .filter(horizon -> now.isBefore(snapshot.evaluationCutoff().plus(horizon)))
                        .count())
                .sum();
        long approximatePopulation = values.stream()
                .mapToLong(snapshot -> properties.getOutcomeHorizons().stream()
                        .filter(horizon -> observedOutcomes.stream().anyMatch(outcome ->
                                outcome.marketId().equals(snapshot.marketId())
                                        && outcome.evaluationCutoff().equals(snapshot.evaluationCutoff())
                                        && outcome.horizon().value().equals(horizon)
                                        && outcome.status() == ObservedMarketOutcomeStatus.AVAILABLE))
                        .count())
                .sum();

        ResearchReadiness readiness = new ResearchReadiness(
                properties.isEnabled(), markets, properties.getObservationCadence(),
                properties.getOutcomeHorizons(), EvidenceAcquisitionPolicy.STRICT_OBSERVED.name(),
                latestEvidence, values.size(), snapshotsByMarket,
                values.stream().map(FeatureSnapshot::evaluationCutoff).min(Comparator.naturalOrder()).orElse(null),
                values.stream().map(FeatureSnapshot::evaluationCutoff).max(Comparator.naturalOrder()).orElse(null),
                outcomeCounts, available, pending, approximatePopulation, s1(values, now));
        log.info("Research readiness computation completed markets={} snapshots={} outcomes={} signals={} "
                        + "snapshotLoadMs={} outcomeLoadMs={} latestEvidenceMs={} totalMs={}",
                markets.size(), values.size(), observedOutcomes.size(), readiness.s1().totalSignals(),
                snapshotLoadDurationMs, outcomeLoadDurationMs, latestEvidenceDurationMs,
                elapsedMillis(computationStartedAt));
        return readiness;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private record CachedReadiness(ResearchReadiness value, Instant expiresAt) { }

    private S1Readiness s1(List<FeatureSnapshot> snapshots, Instant now) {
        long startedAt = System.nanoTime();
        List<Signal> signals = snapshots.stream().map(this::signal).flatMap(Optional::stream).toList();
        long longs = signals.stream().filter(value -> value.direction().equals("LONG")).count();
        long shorts = signals.stream().filter(value -> value.direction().equals("SHORT")).count();
        Set<String> regimes = signals.stream().map(Signal::regime).flatMap(Optional::stream).collect(Collectors.toSet());
        RemoteCheckBudget remoteBudget = new RemoteCheckBudget(properties.getMaxRemoteEvidenceChecks());
        long replayChecks = signals.size() * 2L;
        long feeChecks = signals.size() * 2L;
        long replayAvailable;
        long feeAvailable;
        S1RemoteMetrics remoteMetrics = new S1RemoteMetrics();
        try (ExecutorService executor = Executors.newFixedThreadPool(properties.getReadinessRemoteConcurrency())) {
            Map<ReplayKey, CompletableFuture<Long>> replayFutures = new HashMap<>();
            Map<Instant, CompletableFuture<Long>> feeFutures = new HashMap<>();
            for (Signal signal : signals) {
                scheduleReplay(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff(),
                        replayFutures, executor, remoteBudget, remoteMetrics);
                scheduleReplay(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff().plus(Duration.ofHours(1)),
                        replayFutures, executor, remoteBudget, remoteMetrics);
                if (fees != null && properties.getFeeCoverage().getBrokerAccountId() != null) {
                    scheduleFee(signal.snapshot().evaluationCutoff(), now, feeFutures, executor, remoteBudget, remoteMetrics);
                    scheduleFee(signal.snapshot().evaluationCutoff().plus(Duration.ofHours(1)), now,
                            feeFutures, executor, remoteBudget, remoteMetrics);
                }
            }
            replayAvailable = signals.stream()
                    .mapToLong(signal -> replayAvailable(signal, replayFutures)).sum();
            feeAvailable = signals.isEmpty() ? 0L : signals.stream()
                    .mapToLong(signal -> feeAvailable(signal, now, feeFutures, remoteBudget)).sum();
        }
        double replayCoverage = coverage(replayAvailable, replayChecks);
        double feeCoverage = coverage(feeAvailable, feeChecks);
        int replayKeys = replayKeyCount(signals);
        int feeKeys = fees != null && properties.getFeeCoverage().getBrokerAccountId() != null
                ? feeKeyCount(signals) : 0;
        log.info("Research S1 checks completed signals={} replayChecks={} feeChecks={} "
                        + "remoteChecksConsumed={} remoteBudgetExhausted={} durationMs={} "
                        + "replayCalls={} replayRemoteMs={} replayKeys={} replayReuses={} "
                        + "feeCalls={} feeRemoteMs={} feeKeys={} feeReuses={}",
                signals.size(), replayChecks, feeChecks, remoteBudget.consumed(), remoteBudget.exhausted(),
                elapsedMillis(startedAt), remoteMetrics.replayCalls(), remoteMetrics.replayDurationMs(),
                replayKeys, replayChecks - replayKeys, remoteMetrics.feeCalls(),
                remoteMetrics.feeDurationMs(), feeKeys, feeKeys == 0 ? 0 : feeChecks - feeKeys);
        List<String> blockers = new java.util.ArrayList<>();
        if (signals.size() < 60) blockers.add("signal_count_below_60");
        if (longs < 30) blockers.add("long_signal_count_below_30");
        if (shorts < 30) blockers.add("short_signal_count_below_30");
        if (signals.isEmpty()) {
            blockers.add("no_signal_period");
        } else {
            Instant earliest = signals.stream().map(Signal::snapshot).map(FeatureSnapshot::evaluationCutoff)
                    .min(Comparator.naturalOrder()).orElse(now);
            if (Duration.between(earliest, now).compareTo(Duration.ofDays(14)) < 0) {
                blockers.add("signal_period_below_14_days");
            }
        }
        if (regimes.size() < 2) blockers.add("aggregate_regime_count_below_2");
        if (replayCoverage < 0.90d) blockers.add("trusted_replay_coverage_below_90_percent");
        if (feeCoverage < 1.0d) blockers.add("fee_coverage_incomplete");
        if (remoteBudget.exhausted()) blockers.add("remote_evidence_check_budget_exceeded");
        return new S1Readiness(signals.size(), longs, shorts, regimes, replayChecks, replayAvailable,
                replayCoverage, feeChecks, feeAvailable, feeCoverage, blockers.isEmpty(), blockers);
    }

    private Optional<Signal> signal(FeatureSnapshot snapshot) {
        List<com.hope.trading.market_intelligence.domain.feature.FeatureValue> featureValues =
                snapshot.values() == null ? List.of() : snapshot.values();
        String status = featureValues.stream().filter(value -> value.name().equals("trend.trigger.break_status"))
                .filter(CategoricalFeatureValue.class::isInstance).map(CategoricalFeatureValue.class::cast)
                .filter(value -> value.status().name().equals("PRESENT")).map(CategoricalFeatureValue::value)
                .findFirst().orElse(null);
        String direction = switch (Objects.toString(status, "")) {
            case "CONFIRMED_BULLISH_BREAK" -> "LONG";
            case "CONFIRMED_BEARISH_BREAK" -> "SHORT";
            default -> null;
        };
        if (direction == null) return Optional.empty();
        Optional<String> regime = Optional.empty();
        if (artifacts != null) {
            try {
                List<ProducedArtifact> found = artifacts.find(snapshot.analysisExecutionId(),
                        ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT, ProductionArtifactTypes.V1);
                regime = found.stream().map(value -> value.artifact().content())
                        .filter(TrendContextCapabilityContent.class::isInstance)
                        .map(TrendContextCapabilityContent.class::cast).map(TrendContextCapabilityContent::assessment)
                        .filter(Objects::nonNull).map(value -> value.regime().name()).findFirst();
            } catch (RuntimeException ignored) {
                // A missing aggregate artifact makes the regime gate fail closed.
            }
        }
        return Optional.of(new Signal(snapshot, direction, regime));
    }

    private void scheduleReplay(UUID marketId, Instant cutoff,
                                 Map<ReplayKey, CompletableFuture<Long>> cache,
                                 ExecutorService executor, RemoteCheckBudget budget,
                                 S1RemoteMetrics metrics) {
        ReplayKey key = new ReplayKey(marketId, cutoff);
        cache.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(
                () -> replayAvailableAt(marketId, cutoff, budget, metrics), executor)
                .exceptionally(error -> {
                    log.warn("Bounded replay readiness check failed", error);
                    budget.markReplayUnavailable();
                    return 0L;
                }));
    }

    private long replayAvailable(Signal signal, Map<ReplayKey, CompletableFuture<Long>> cache) {
        if (replay == null) return 0L;
        return cache.get(new ReplayKey(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff())).join()
                + cache.get(new ReplayKey(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff()
                .plus(Duration.ofHours(1)))).join();
    }

    private long replayAvailableAt(UUID marketId, Instant cutoff, RemoteCheckBudget budget, S1RemoteMetrics metrics) {
        if (replay == null) return 0L;
        if (budget.replayUnavailable()) return 0L;
        if (!budget.tryConsume()) return 0L;
        long startedAt = System.nanoTime();
        long result;
        try {
            result = "AVAILABLE".equalsIgnoreCase(
                    replay.replay(marketId, REPLAY_DEPTH, cutoff,
                            OrderBookReplayIdentity.forRequest(marketId, REPLAY_DEPTH, cutoff)).status()) ? 1L : 0L;
        } catch (RuntimeException ignored) {
            budget.markReplayUnavailable();
            result = 0L;
        } finally {
            metrics.recordReplay(Duration.ofNanos(System.nanoTime() - startedAt));
        }
        return result;
    }

    private void scheduleFee(Instant executionTime, Instant researchCutoff,
                              Map<Instant, CompletableFuture<Long>> cache,
                              ExecutorService executor, RemoteCheckBudget budget,
                              S1RemoteMetrics metrics) {
        cache.computeIfAbsent(executionTime, ignored -> CompletableFuture.supplyAsync(
                () -> feeAvailableAt(executionTime, researchCutoff, budget, metrics), executor)
                .exceptionally(error -> {
                    log.warn("Bounded historical fee readiness check failed", error);
                    budget.markFeeUnavailable();
                    return 0L;
                }));
    }

    private long feeAvailable(Signal signal, Instant researchCutoff,
                              Map<Instant, CompletableFuture<Long>> cache, RemoteCheckBudget budget) {
        var config = properties.getFeeCoverage();
        if (fees == null || config.getBrokerAccountId() == null) {
            return 0L;
        }
        return cache.get(signal.snapshot().evaluationCutoff()).join()
                + cache.get(signal.snapshot().evaluationCutoff().plus(Duration.ofHours(1))).join();
    }

    private long feeAvailableAt(Instant executionTime, Instant researchCutoff,
                                RemoteCheckBudget budget, S1RemoteMetrics metrics) {
        if (fees == null) return 0L;
        if (budget.feeUnavailable()) return 0L;
        if (!budget.tryConsume()) return 0L;
        long startedAt = System.nanoTime();
        var config = properties.getFeeCoverage();
        long result;
        try {
            var response = fees.findEligible(config.getBrokerAccountId(), config.getProvider(), config.getProductType(),
                    config.getInstrument(), executionTime, researchCutoff);
            result = response != null ? 1L : 0L;
            if (result == 0L) {
                log.debug("No eligible historical fee schedule for executionTime={} researchCutoff={} status={}",
                        executionTime, researchCutoff, response == null ? "null" : "present");
            }
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                log.debug("No eligible historical fee schedule for executionTime={} researchCutoff={}",
                        executionTime, researchCutoff);
                result = 0L;
            } else {
                log.warn("Historical fee lookup failed for executionTime={} researchCutoff={} status={}",
                        executionTime, researchCutoff, exception.status(), exception);
                budget.markFeeUnavailable();
                result = 0L;
            }
        } catch (RuntimeException ignored) {
            log.warn("Historical fee lookup failed for executionTime={} researchCutoff={}",
                    executionTime, researchCutoff, ignored);
            budget.markFeeUnavailable();
            result = 0L;
        } finally {
            metrics.recordFee(Duration.ofNanos(System.nanoTime() - startedAt));
        }
        return result;
    }

    private int replayKeyCount(List<Signal> signals) {
        return (int) signals.stream()
                .map(signal -> new ReplayKey(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff()))
                .flatMap(key -> java.util.stream.Stream.of(key,
                        new ReplayKey(key.marketId(), key.cutoff().plus(Duration.ofHours(1)))))
                .distinct()
                .count();
    }

    private int feeKeyCount(List<Signal> signals) {
        return signals.stream()
                .map(signal -> signal.snapshot().evaluationCutoff())
                .flatMap(cutoff -> java.util.stream.Stream.of(cutoff, cutoff.plus(Duration.ofHours(1))))
                .distinct()
                .mapToInt(ignored -> 1)
                .sum();
    }

    private double coverage(long available, long checks) {
        return checks == 0 ? 0.0d : (double) available / checks;
    }

    private record Signal(FeatureSnapshot snapshot, String direction, Optional<String> regime) { }

    private record ReplayKey(UUID marketId, Instant cutoff) { }

    private static final class S1RemoteMetrics {
        private final java.util.concurrent.atomic.LongAdder replayCalls = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder replayDurationNanos = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder feeCalls = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder feeDurationNanos = new java.util.concurrent.atomic.LongAdder();

        private void recordReplay(Duration duration) {
            replayCalls.increment();
            replayDurationNanos.add(duration.toNanos());
        }

        private void recordFee(Duration duration) {
            feeCalls.increment();
            feeDurationNanos.add(duration.toNanos());
        }

        private long replayCalls() {
            return replayCalls.sum();
        }

        private long replayDurationMs() {
            return Duration.ofNanos(replayDurationNanos.sum()).toMillis();
        }

        private long feeCalls() {
            return feeCalls.sum();
        }

        private long feeDurationMs() {
            return Duration.ofNanos(feeDurationNanos.sum()).toMillis();
        }
    }

    private static final class RemoteCheckBudget {
        private final int maximum;
        private final java.util.concurrent.atomic.AtomicInteger remaining;
        private final java.util.concurrent.atomic.AtomicBoolean exhausted = new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean replayUnavailable = new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean feeUnavailable = new java.util.concurrent.atomic.AtomicBoolean();

        private RemoteCheckBudget(int maximum) {
            this.maximum = maximum;
            this.remaining = new java.util.concurrent.atomic.AtomicInteger(maximum);
        }

        private boolean tryConsume() {
            while (true) {
                int current = remaining.get();
                if (current == 0) {
                    exhausted.set(true);
                    return false;
                }
                if (remaining.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        private boolean exhausted() {
            return exhausted.get();
        }

        private int consumed() {
            return maximum - Math.max(0, remaining.get());
        }

        private boolean replayUnavailable() {
            return replayUnavailable.get();
        }

        private void markReplayUnavailable() {
            replayUnavailable.set(true);
        }

        private boolean feeUnavailable() {
            return feeUnavailable.get();
        }

        private void markFeeUnavailable() {
            feeUnavailable.set(true);
        }
    }

    private Map<UUID, Instant> latestEvidence(Instant now, Set<UUID> markets) {
        if (markets.isEmpty()) {
            return Map.of();
        }
        ExecutorService executor = Executors.newFixedThreadPool(properties.getReadinessRemoteConcurrency());
        try {
            Map<UUID, CompletableFuture<Optional<Instant>>> futures = new LinkedHashMap<>();
            for (UUID marketId : markets) {
                futures.put(marketId, CompletableFuture.supplyAsync(
                                () -> latestEvidenceAt(marketId, now), executor)
                        .completeOnTimeout(Optional.empty(), properties.getReadinessRemoteTimeout().toMillis(),
                                TimeUnit.MILLISECONDS)
                        .exceptionally(ignored -> Optional.empty()));
            }

            Map<UUID, Instant> result = new LinkedHashMap<>();
            futures.forEach((marketId, future) -> future.join().ifPresent(value -> result.put(marketId, value)));
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<Instant> latestEvidenceAt(UUID marketId, Instant now) {
        try {
            HistoricalOhlcClient.HistoricalOhlcSeriesResponse response = historicalOhlc.findHistoricalOhlc(
                    marketId, "FIFTEEN_MINUTES", now, 1);
            return response.observations().stream()
                    .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::knownAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        } catch (RuntimeException ignored) {
            // Readiness remains available when Market Data is temporarily unavailable.
            return Optional.empty();
        }
    }
}
