package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookReplayClient;
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
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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

@Service
public class ResearchReadinessService {
    private static final String PROJECTION_VERSION = "feature-projection-v1";
    private static final int REPLAY_DEPTH = 25;
    private static final int MAX_REMOTE_EVIDENCE_CHECKS = 400;
    private final ResearchLifecycleProperties properties;
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final HistoricalOhlcClient historicalOhlc;
    private final ArtifactPersistencePort artifacts;
    private final OrderBookReplayClient replay;
    private final HistoricalFeeScheduleClient fees;
    private final Clock clock;

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
        Set<UUID> markets = properties.getMarketIds();
        List<FeatureSnapshot> values = markets.isEmpty()
                ? List.of()
                : snapshots.findByPopulation(
                        markets, Instant.EPOCH, now.plusSeconds(1), PROJECTION_VERSION,
                        EvidenceAcquisitionPolicy.STRICT_OBSERVED.name());
        Set<java.time.Duration> configuredHorizons = Set.copyOf(properties.getOutcomeHorizons());
        List<ObservedMarketOutcome> observedOutcomes = outcomes.findByMarkets(markets).stream()
                .filter(outcome -> configuredHorizons.contains(outcome.horizon().value()))
                .toList();

        Map<UUID, Instant> latestEvidence = latestEvidence(now, markets);
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

        return new ResearchReadiness(
                properties.isEnabled(), markets, properties.getObservationCadence(),
                properties.getOutcomeHorizons(), EvidenceAcquisitionPolicy.STRICT_OBSERVED.name(),
                latestEvidence, values.size(), snapshotsByMarket,
                values.stream().map(FeatureSnapshot::evaluationCutoff).min(Comparator.naturalOrder()).orElse(null),
                values.stream().map(FeatureSnapshot::evaluationCutoff).max(Comparator.naturalOrder()).orElse(null),
                outcomeCounts, available, pending, approximatePopulation, s1(values, now));
    }

    private S1Readiness s1(List<FeatureSnapshot> snapshots, Instant now) {
        List<Signal> signals = snapshots.stream().map(this::signal).flatMap(Optional::stream).toList();
        long longs = signals.stream().filter(value -> value.direction().equals("LONG")).count();
        long shorts = signals.stream().filter(value -> value.direction().equals("SHORT")).count();
        Set<String> regimes = signals.stream().map(Signal::regime).flatMap(Optional::stream).collect(Collectors.toSet());
        Map<ReplayKey, Long> replayCache = new java.util.HashMap<>();
        Map<Instant, Long> feeCache = new java.util.HashMap<>();
        RemoteCheckBudget remoteBudget = new RemoteCheckBudget(MAX_REMOTE_EVIDENCE_CHECKS);
        long replayChecks = signals.size() * 2L;
        long replayAvailable = signals.stream()
                .mapToLong(value -> replayAvailable(value, replayCache, remoteBudget)).sum();
        double replayCoverage = coverage(replayAvailable, replayChecks);
        long feeChecks = signals.size() * 2L;
        long feeAvailable = signals.stream()
                .mapToLong(value -> feeAvailable(value, now, feeCache, remoteBudget)).sum();
        double feeCoverage = coverage(feeAvailable, feeChecks);
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

    private long replayAvailable(Signal signal, Map<ReplayKey, Long> cache, RemoteCheckBudget budget) {
        if (replay == null) return 0L;
        return replayAvailableAt(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff(), cache, budget)
                + replayAvailableAt(signal.snapshot().marketId(), signal.snapshot().evaluationCutoff()
                .plus(Duration.ofHours(1)), cache, budget);
    }

    private long replayAvailableAt(UUID marketId, Instant cutoff,
                                   Map<ReplayKey, Long> cache, RemoteCheckBudget budget) {
        ReplayKey key = new ReplayKey(marketId, cutoff);
        Long cached = cache.get(key);
        if (cached != null) return cached;
        if (!budget.tryConsume()) return 0L;
        long result;
        try {
            result = "AVAILABLE".equalsIgnoreCase(replay.replay(marketId, REPLAY_DEPTH, cutoff).status()) ? 1L : 0L;
        } catch (RuntimeException ignored) { result = 0L; }
        cache.put(key, result);
        return result;
    }

    private long feeAvailable(Signal signal, Instant researchCutoff,
                              Map<Instant, Long> cache, RemoteCheckBudget budget) {
        var config = properties.getFeeCoverage();
        if (fees == null || config.getBrokerAccountId() == null) return 0L;
        return feeAvailableAt(signal.snapshot().evaluationCutoff(), researchCutoff, cache, budget)
                + feeAvailableAt(signal.snapshot().evaluationCutoff().plus(Duration.ofHours(1)),
                researchCutoff, cache, budget);
    }

    private long feeAvailableAt(Instant executionTime, Instant researchCutoff,
                                Map<Instant, Long> cache, RemoteCheckBudget budget) {
        Long cached = cache.get(executionTime);
        if (cached != null) return cached;
        if (!budget.tryConsume()) return 0L;
        var config = properties.getFeeCoverage();
        long result;
        try {
            var response = fees.findEligible(config.getBrokerAccountId(), config.getProvider(), config.getProductType(),
                    config.getInstrument(), executionTime, researchCutoff);
            result = response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null ? 1L : 0L;
        } catch (RuntimeException ignored) { result = 0L; }
        cache.put(executionTime, result);
        return result;
    }

    private double coverage(long available, long checks) {
        return checks == 0 ? 0.0d : (double) available / checks;
    }

    private record Signal(FeatureSnapshot snapshot, String direction, Optional<String> regime) { }

    private record ReplayKey(UUID marketId, Instant cutoff) { }

    private static final class RemoteCheckBudget {
        private int remaining;
        private boolean exhausted;

        private RemoteCheckBudget(int maximum) {
            this.remaining = maximum;
        }

        private boolean tryConsume() {
            if (remaining == 0) {
                exhausted = true;
                return false;
            }
            remaining--;
            return true;
        }

        private boolean exhausted() {
            return exhausted;
        }
    }

    private Map<UUID, Instant> latestEvidence(Instant now, Set<UUID> markets) {
        Map<UUID, Instant> result = new LinkedHashMap<>();
        for (UUID marketId : markets) {
            try {
                HistoricalOhlcClient.HistoricalOhlcSeriesResponse response = historicalOhlc.findHistoricalOhlc(
                        marketId, "FIFTEEN_MINUTES", now, 1);
                response.observations().stream()
                        .map(HistoricalOhlcClient.HistoricalOhlcEvidenceResponse::knownAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .ifPresent(value -> result.put(marketId, value));
            } catch (RuntimeException ignored) {
                // Readiness remains available when Market Data is temporarily unavailable.
            }
        }
        return result;
    }
}
