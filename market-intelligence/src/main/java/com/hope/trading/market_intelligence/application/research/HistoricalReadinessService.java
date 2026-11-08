package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
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
import java.util.stream.Collectors;

@Service
public class HistoricalReadinessService {
    private static final String PROJECTION_VERSION = "feature-projection-v1";
    private static final long MAX_READINESS_OBSERVATIONS = 100_000;

    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final AnalysisExecutionRepository executions;
    private final Clock clock;

    public HistoricalReadinessService(FeatureSnapshotRepository snapshots,
                                      ObservedMarketOutcomeRepository outcomes,
                                      AnalysisExecutionRepository executions,
                                      Clock clock) {
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.executions = executions;
        this.clock = clock;
    }

    public HistoricalReadinessResponse inspect(HistoricalReadinessRequest request, String correlationId) {
        if (request.marketIds().isEmpty() || request.marketIds().size() > 2
                || !request.cutoffStart().isBefore(request.cutoffEnd())
                || request.datasetProfile().isBlank()
                || request.maxObservations() <= 0
                || request.maxObservations() > MAX_READINESS_OBSERVATIONS) {
            throw new HistoricalResearchException(400, "INVALID_READINESS_REQUEST", "historical readiness request is invalid");
        }
        int observationLimit = request.maxObservations() == Long.MAX_VALUE
                ? Integer.MAX_VALUE : Math.toIntExact(Math.min(request.maxObservations() + 1, Integer.MAX_VALUE));
        List<FeatureSnapshot> values = snapshots.findByPopulation(request.marketIds(), request.cutoffStart(),
                request.cutoffEnd(), PROJECTION_VERSION, EvidenceAcquisitionPolicy.STRICT_OBSERVED.name(),
                observationLimit);
        int outcomeLimit = Math.toIntExact(request.maxObservations() + 1);
        List<ObservedMarketOutcome> observed = outcomes.findByMarketsAndCutoffRange(request.marketIds(),
                request.cutoffStart(), request.cutoffEnd(), outcomeLimit);
        boolean outcomeLimitExceeded = observed.size() > request.maxObservations();
        if (outcomeLimitExceeded) observed = observed.subList(0, Math.toIntExact(request.maxObservations()));
        Map<String, Long> outcomeCounts = observed.stream().collect(Collectors.groupingBy(
                value -> value.horizon().value() + ":" + value.status(), LinkedHashMap::new, Collectors.counting()));
        long eligibleOutcomes = outcomes.countByMarketsAndCutoffRangeAndStatus(request.marketIds(),
                request.cutoffStart(), request.cutoffEnd(), ObservedMarketOutcomeStatus.AVAILABLE);
        Map<UUID, Long> byMarket = values.stream().collect(Collectors.groupingBy(
                FeatureSnapshot::marketId, LinkedHashMap::new, Collectors.counting()));
        long postCutoff = snapshots.countByMarketsAfter(request.marketIds(), request.cutoffEnd(), PROJECTION_VERSION,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED.name());
        String provenance = provenanceStatus(values);
        List<String> blockers = new java.util.ArrayList<>();
        if (values.isEmpty()) blockers.add("NO_FEATURE_SNAPSHOTS");
        if (values.size() > request.maxObservations()) blockers.add("OBSERVATION_LIMIT_EXCEEDED");
        if (outcomeLimitExceeded) blockers.add("OUTCOME_LIMIT_EXCEEDED");
        if (postCutoff > 0) blockers.add("POST_CUTOFF_EVIDENCE");
        if ("UNKNOWN".equals(provenance)) blockers.add("PROVENANCE_UNKNOWN");
        HistoricalReadinessVerdict verdict = values.isEmpty() || eligibleOutcomes == 0
                ? HistoricalReadinessVerdict.INSUFFICIENT
                : "UNKNOWN".equals(provenance) ? HistoricalReadinessVerdict.UNKNOWN
                : !blockers.isEmpty() ? HistoricalReadinessVerdict.BLOCKED
                : HistoricalReadinessVerdict.READY;
        return new HistoricalReadinessResponse(verdict, Set.copyOf(request.marketIds()), request.cutoffStart(),
                request.cutoffEnd(), request.cutoffStart(), request.cutoffEnd(), values.size(), values.size(),
                eligibleOutcomes, postCutoff, values.stream().map(FeatureSnapshot::evaluationCutoff)
                .min(Comparator.naturalOrder()).orElse(null), values.stream().map(FeatureSnapshot::evaluationCutoff)
                .max(Comparator.naturalOrder()).orElse(null), Map.copyOf(byMarket), Map.copyOf(outcomeCounts),
                PROJECTION_VERSION, provenance,
                eligibleOutcomes == 0 ? "INSUFFICIENT" : "AVAILABLE", List.copyOf(blockers), clock.instant(),
                correlationId);
    }

    private String provenanceStatus(List<FeatureSnapshot> values) {
        if (values.isEmpty()) return "UNKNOWN";
        boolean complete = values.stream().allMatch(value -> !value.provenance().isEmpty()
                && executions.findById(value.analysisExecutionId()).flatMap(execution -> execution.result()).isPresent());
        return complete ? "VALID" : "UNKNOWN";
    }
}
