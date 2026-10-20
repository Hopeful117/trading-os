package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResearchIntegrityService {
    private static final String PROJECTION_VERSION = "feature-projection-v1";
    private static final String EVIDENCE_POLICY = EvidenceAcquisitionPolicy.STRICT_OBSERVED.name();

    private final ResearchLifecycleProperties properties;
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final Clock clock;
    private volatile CachedIntegrity cachedIntegrity;

    public ResearchIntegrityService(
            ResearchLifecycleProperties properties,
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            Clock clock
    ) {
        this.properties = properties;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    public ResearchIntegrity current() {
        Instant now = clock.instant();
        CachedIntegrity cached = cachedIntegrity;
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        synchronized (this) {
            now = clock.instant();
            cached = cachedIntegrity;
            if (cached != null && now.isBefore(cached.expiresAt())) {
                return cached.value();
            }
            ResearchIntegrity computed = compute(now);
            cachedIntegrity = new CachedIntegrity(
                    computed, now.plus(cacheDuration()));
            return computed;
        }
    }

    private ResearchIntegrity compute(Instant now) {
        Set<UUID> markets = properties.getMarketIds();
        try {
            properties.validate();
        } catch (RuntimeException exception) {
            return unknown(now, markets, "configuration_unavailable");
        }
        if (!properties.isEnabled()) {
            return unknown(now, markets, "research_lifecycle_disabled");
        }

        List<String> warnings = new ArrayList<>();
        Map<UUID, ResearchIntegrity.MarketAcquisitionIntegrity> acquisition = acquisition(
                now, markets, warnings);
        MaturationProjection maturation = maturation(now, markets, warnings);
        ResearchIntegrityStatus status = aggregateStatus(acquisition.values(), maturation, warnings);

        return new ResearchIntegrity(
                now,
                true,
                markets,
                properties.getObservationCadence(),
                status,
                acquisition,
                maturation.byHorizon(),
                maturation.overdueCount(),
                maturation.latestSuccessfulOutcomeComputedAt(),
                maturation.timestampAnomalyCount(),
                List.copyOf(new LinkedHashSet<>(warnings)));
    }

    private Map<UUID, ResearchIntegrity.MarketAcquisitionIntegrity> acquisition(
            Instant now, Set<UUID> markets, List<String> warnings) {
        Instant expectedCutoff;
        try {
            expectedCutoff = ResearchLifecycleService.alignedCutoff(
                    now.minus(properties.getFixedDelay()), properties.getObservationCadence());
        } catch (RuntimeException exception) {
            warnings.add("acquisition_expectation_unavailable");
            return markets.stream().collect(Collectors.toMap(
                    market -> market,
                    market -> new ResearchIntegrity.MarketAcquisitionIntegrity(
                            ResearchIntegrityStatus.UNKNOWN, null, null, 0L),
                    (left, right) -> left,
                    LinkedHashMap::new));
        }

        Map<UUID, ResearchIntegrity.MarketAcquisitionIntegrity> result = new LinkedHashMap<>();
        for (UUID market : markets) {
            try {
                Optional<FeatureSnapshot> latest = snapshots.findLatestByMarket(
                        market, now.plusNanos(1), PROJECTION_VERSION, EVIDENCE_POLICY);
                if (latest.isEmpty()) {
                    result.put(market, new ResearchIntegrity.MarketAcquisitionIntegrity(
                            ResearchIntegrityStatus.NO_DATA_YET, expectedCutoff, null, 0L));
                    warnings.add("no_snapshot:" + market);
                    continue;
                }
                Instant latestCutoff = latest.get().evaluationCutoff();
                if (latestCutoff.isAfter(now)) {
                    result.put(market, new ResearchIntegrity.MarketAcquisitionIntegrity(
                            ResearchIntegrityStatus.UNKNOWN, expectedCutoff, latestCutoff, 0L));
                    warnings.add("future_latest_snapshot:" + market);
                    continue;
                }
                long lagSeconds = Duration.between(latestCutoff, expectedCutoff).getSeconds();
                ResearchIntegrityStatus status = lagSeconds <= 0
                        ? ResearchIntegrityStatus.HEALTHY
                        : lagSeconds <= properties.getObservationCadence().toSeconds()
                        ? ResearchIntegrityStatus.DEGRADED
                        : ResearchIntegrityStatus.STALE;
                if (status != ResearchIntegrityStatus.HEALTHY) {
                    warnings.add(status.name().toLowerCase() + ":" + market);
                }
                result.put(market, new ResearchIntegrity.MarketAcquisitionIntegrity(
                        status, expectedCutoff, latestCutoff, lagSeconds));
            } catch (RuntimeException exception) {
                result.put(market, new ResearchIntegrity.MarketAcquisitionIntegrity(
                        ResearchIntegrityStatus.UNKNOWN, expectedCutoff, null, 0L));
                warnings.add("snapshot_lookup_failed:" + market);
            }
        }
        return result;
    }

    private MaturationProjection maturation(Instant now, Set<UUID> markets, List<String> warnings) {
        List<Duration> horizons = properties.getOutcomeHorizons();
        Duration maximumHorizon = horizons.stream().max(Duration::compareTo).orElse(Duration.ZERO);
        Instant start = now.minus(maximumHorizon).minus(properties.getObservationCadence());
        Instant end = now.plusSeconds(1);
        List<FeatureSnapshot> candidates;
        List<ObservedMarketOutcome> observedOutcomes;
        try {
            candidates = snapshots.findByPopulation(markets, start, end, PROJECTION_VERSION, EVIDENCE_POLICY);
            observedOutcomes = outcomes.findByMarketsAndCutoffRange(markets, start, end);
        } catch (RuntimeException exception) {
            warnings.add("maturation_source_unavailable");
            return MaturationProjection.unknown(horizons);
        }

        Map<OutcomeKey, List<ObservedMarketOutcome>> outcomesByKey = observedOutcomes.stream()
                .collect(Collectors.groupingBy(
                        outcome -> new OutcomeKey(outcome.marketId(), outcome.evaluationCutoff(),
                                outcome.horizon().value()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, MutableMaturation> byHorizon = horizons.stream()
                .collect(Collectors.toMap(value -> value.toString(), value -> new MutableMaturation(),
                        (left, right) -> left, LinkedHashMap::new));
        Instant latestSuccessful = null;
        int overdue = 0;
        int timestampAnomalies = 0;

        for (FeatureSnapshot snapshot : candidates) {
            if (snapshot.evaluationCutoff().isAfter(now)) {
                timestampAnomalies++;
                warnings.add("future_snapshot_cutoff");
            }
            for (Duration horizon : horizons) {
                MutableMaturation summary = byHorizon.get(horizon.toString());
                Instant maturity = snapshot.evaluationCutoff().plus(horizon);
                OutcomeKey key = new OutcomeKey(snapshot.marketId(), snapshot.evaluationCutoff(), horizon);
                List<ObservedMarketOutcome> matching = outcomesByKey.getOrDefault(key, List.of());
                if (now.isBefore(maturity)) {
                    summary.pendingCount++;
                    continue;
                }
                summary.dueSnapshotCount++;
                if (matching.isEmpty()) {
                    summary.overdueCount++;
                    overdue++;
                    continue;
                }
                if (matching.size() > 1) {
                    warnings.add("multiple_outcome_records:" + snapshot.marketId());
                }
                summary.latestComputedAt = latest(summary.latestComputedAt, matching);
                EnumSet<ObservedMarketOutcomeStatus> statuses = matching.stream()
                        .map(ObservedMarketOutcome::status)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ObservedMarketOutcomeStatus.class)));
                if (statuses.contains(ObservedMarketOutcomeStatus.AVAILABLE)) {
                    summary.availableCount++;
                    latestSuccessful = latest(latestSuccessful, matching);
                } else if (statuses.contains(ObservedMarketOutcomeStatus.INCOMPLETE)) {
                    summary.incompleteCount++;
                } else if (statuses.contains(ObservedMarketOutcomeStatus.UNAVAILABLE)) {
                    summary.unavailableCount++;
                } else {
                    summary.pendingCount++;
                    warnings.add("matured_outcome_pending");
                }
                for (ObservedMarketOutcome outcome : matching) {
                    if (!outcome.maturityAt().equals(maturity)
                            || outcome.computedAt().isBefore(outcome.evaluationCutoff())
                            || outcome.computedAt().isAfter(now)) {
                        timestampAnomalies++;
                        warnings.add("outcome_timestamp_incoherent");
                    }
                }
            }
        }

        Map<String, ResearchIntegrity.OutcomeMaturationIntegrity> immutable = new LinkedHashMap<>();
        byHorizon.forEach((horizon, summary) -> immutable.put(horizon, summary.toImmutable()));
        return new MaturationProjection(immutable, overdue, latestSuccessful, timestampAnomalies);
    }

    private Instant latest(Instant current, List<ObservedMarketOutcome> outcomes) {
        return outcomes.stream().map(ObservedMarketOutcome::computedAt)
                .max(Instant::compareTo)
                .map(value -> current == null || value.isAfter(current) ? value : current)
                .orElse(current);
    }

    private ResearchIntegrityStatus aggregateStatus(
            Collection<ResearchIntegrity.MarketAcquisitionIntegrity> acquisition,
            MaturationProjection maturation,
            List<String> warnings) {
        if (acquisition.iterator().hasNext()) {
            boolean anyUnknown = false;
            boolean anyStale = false;
            boolean anyNoData = false;
            boolean anyDegraded = false;
            for (ResearchIntegrity.MarketAcquisitionIntegrity value : acquisition) {
                anyUnknown |= value.status() == ResearchIntegrityStatus.UNKNOWN;
                anyStale |= value.status() == ResearchIntegrityStatus.STALE;
                anyNoData |= value.status() == ResearchIntegrityStatus.NO_DATA_YET;
                anyDegraded |= value.status() == ResearchIntegrityStatus.DEGRADED;
            }
            if (anyUnknown) return ResearchIntegrityStatus.UNKNOWN;
            if (anyStale) return ResearchIntegrityStatus.STALE;
            if (anyNoData && acquisitionStatusCount(acquisition, ResearchIntegrityStatus.NO_DATA_YET)
                    == acquisition.size()) {
                return ResearchIntegrityStatus.NO_DATA_YET;
            }
            if (anyNoData || anyDegraded || maturation.overdueCount() > 0
                    || maturation.timestampAnomalyCount() > 0) {
                return ResearchIntegrityStatus.DEGRADED;
            }
        }
        if (maturation.timestampAnomalyCount() > 0 || maturation.overdueCount() > 0
                || warnings.stream().anyMatch(value -> value.contains("matured_outcome_pending"))) {
            return ResearchIntegrityStatus.DEGRADED;
        }
        return warnings.stream().anyMatch(value -> value.contains("unavailable") || value.contains("failed"))
                ? ResearchIntegrityStatus.UNKNOWN : ResearchIntegrityStatus.HEALTHY;
    }

    private long acquisitionStatusCount(Collection<ResearchIntegrity.MarketAcquisitionIntegrity> values,
                                       ResearchIntegrityStatus status) {
        long count = 0;
        for (ResearchIntegrity.MarketAcquisitionIntegrity value : values) {
            if (value.status() == status) count++;
        }
        return count;
    }

    private ResearchIntegrity unknown(Instant now, Set<UUID> markets, String warning) {
        return new ResearchIntegrity(now, properties.isEnabled(), markets,
                properties.getObservationCadence(), ResearchIntegrityStatus.UNKNOWN,
                Map.of(), Map.of(), 0, null, 0, List.of(warning));
    }

    private Duration cacheDuration() {
        try {
            Duration duration = properties.getReadinessCacheDuration();
            return duration == null || duration.isNegative() ? Duration.ZERO : duration;
        } catch (RuntimeException exception) {
            return Duration.ZERO;
        }
    }

    private record CachedIntegrity(ResearchIntegrity value, Instant expiresAt) { }

    private record OutcomeKey(UUID marketId, Instant cutoff, Duration horizon) { }

    private record MaturationProjection(
            Map<String, ResearchIntegrity.OutcomeMaturationIntegrity> byHorizon,
            int overdueCount,
            Instant latestSuccessfulOutcomeComputedAt,
            int timestampAnomalyCount
    ) {
        private static MaturationProjection unknown(List<Duration> horizons) {
            Map<String, ResearchIntegrity.OutcomeMaturationIntegrity> values = horizons.stream()
                    .collect(Collectors.toMap(Duration::toString,
                            value -> new ResearchIntegrity.OutcomeMaturationIntegrity(0, 0, 0, 0, 0, 0, null),
                            (left, right) -> left, LinkedHashMap::new));
            return new MaturationProjection(values, 0, null, 0);
        }
    }

    private static final class MutableMaturation {
        private long dueSnapshotCount;
        private long availableCount;
        private long pendingCount;
        private long incompleteCount;
        private long unavailableCount;
        private long overdueCount;
        private Instant latestComputedAt;

        private ResearchIntegrity.OutcomeMaturationIntegrity toImmutable() {
            return new ResearchIntegrity.OutcomeMaturationIntegrity(
                    dueSnapshotCount, availableCount, pendingCount, incompleteCount,
                    unavailableCount, overdueCount, latestComputedAt);
        }
    }

}
