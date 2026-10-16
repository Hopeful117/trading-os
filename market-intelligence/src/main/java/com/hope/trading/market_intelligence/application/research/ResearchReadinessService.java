package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ResearchReadinessService {
    private static final String PROJECTION_VERSION = "feature-projection-v1";
    private final ResearchLifecycleProperties properties;
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final HistoricalOhlcClient historicalOhlc;
    private final Clock clock;

    public ResearchReadinessService(
            ResearchLifecycleProperties properties,
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            HistoricalOhlcClient historicalOhlc,
            Clock clock
    ) {
        this.properties = properties;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.historicalOhlc = historicalOhlc;
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
                outcomeCounts, available, pending, approximatePopulation);
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
