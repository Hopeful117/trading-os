package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.feature.FeatureSnapshotProjectionService;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationRequest;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(ResearchLifecycleService.class);
    private static final String OBJECTIVE = "research-feature-snapshot-v1";
    private static final String PROJECTION_VERSION = "feature-projection-v1";

    private final ResearchLifecycleProperties properties;
    private final AnalysisExecutionService executions;
    private final AnalysisExecutionRepository executionRepository;
    private final FeatureSnapshotProjectionService projections;
    private final FeatureSnapshotRepository snapshots;
    private final MarketOutcomeEvaluationService outcomes;
    private final ResearchArtifactPopulationService artifactPopulation;
    private final Clock clock;

    public ResearchLifecycleService(
            ResearchLifecycleProperties properties,
            AnalysisExecutionService executions,
            AnalysisExecutionRepository executionRepository,
            FeatureSnapshotProjectionService projections,
            FeatureSnapshotRepository snapshots,
            MarketOutcomeEvaluationService outcomes,
            ResearchArtifactPopulationService artifactPopulation,
            Clock clock
    ) {
        this.properties = properties;
        this.executions = executions;
        this.executionRepository = executionRepository;
        this.projections = projections;
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.artifactPopulation = artifactPopulation;
        this.clock = clock;
    }

    public void runOnce() {
        properties.validate();
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        try {
            artifactPopulation.populate(alignedCutoff(now, properties.getObservationCadence()));
        } catch (RuntimeException exception) {
            log.warn("Research artifact population failed; continuing lifecycle errorType={}",
                    exception.getClass().getSimpleName());
        }
        submitLatestObservation(now);
        projectCompletedExecutions(now);
        evaluateMaturedOutcomes(now);
    }

    static Instant alignedCutoff(Instant now, Duration cadence) {
        long cadenceSeconds = cadence.getSeconds();
        if (cadenceSeconds <= 0 || cadence.toNanos() != cadenceSeconds * 1_000_000_000L) {
            throw new IllegalArgumentException("Research cadence must be a whole positive number of seconds");
        }
        long epochSecond = Math.floorDiv(now.getEpochSecond(), cadenceSeconds) * cadenceSeconds;
        return Instant.ofEpochSecond(epochSecond);
    }

    private void submitLatestObservation(Instant now) {
        Instant cutoff = alignedCutoff(now, properties.getObservationCadence());
        for (UUID marketId : properties.getMarketIds()) {
            IdempotencyKey key = new IdempotencyKey(idempotencyKey(marketId, cutoff));
            if (executionRepository.findByIdempotencyKey(key).isEmpty()) {
                try {
                    executions.create(
                            new IntelligenceAnalysisRequest(
                                    UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                                    OBJECTIVE, null, null, null, cutoff,
                                    EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                            key, key.value(), key.value());
                } catch (RuntimeException exception) {
                    log.warn("Research observation submission failed market={} cutoff={} errorType={}",
                            marketId, cutoff, exception.getClass().getSimpleName());
                }
            }
        }
    }

    private void projectCompletedExecutions(Instant now) {
        List<AnalysisExecution> candidates = executionRepository.findByResearchScope(
                properties.getMarketIds(), Instant.EPOCH, now.plusSeconds(1),
                EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        for (AnalysisExecution execution : candidates) {
            if ((execution.status() == AnalysisExecutionStatus.COMPLETED
                    || execution.status() == AnalysisExecutionStatus.PARTIALLY_COMPLETED)
                    && execution.result().isPresent()) {
                try {
                    projections.project(execution.executionId());
                } catch (RuntimeException exception) {
                    log.warn("Research feature projection failed execution={} market={} cutoff={} errorType={} message={}",
                            execution.executionId(), execution.provenance().marketId(),
                            execution.provenance().evaluationCutoff(), exception.getClass().getSimpleName(),
                            exception.getMessage());
                }
            }
        }
    }

    private void evaluateMaturedOutcomes(Instant now) {
        List<FeatureSnapshot> candidates = snapshots.findByPopulation(
                properties.getMarketIds(), Instant.EPOCH, now.plusSeconds(1),
                PROJECTION_VERSION, EvidenceAcquisitionPolicy.STRICT_OBSERVED.name());
        for (FeatureSnapshot snapshot : candidates) {
            for (Duration duration : properties.getOutcomeHorizons()) {
                Instant maturity = snapshot.evaluationCutoff().plus(duration);
                if (now.isBefore(maturity)) {
                    continue;
                }
                try {
                    outcomes.evaluate(new MarketOutcomeEvaluationRequest(
                            snapshot.marketId(), snapshot.evaluationCutoff(),
                            new OutcomeHorizon(duration), snapshot.analysisExecutionId()));
                } catch (RuntimeException exception) {
                    log.warn("Research outcome evaluation failed market={} cutoff={} horizon={} errorType={}",
                            snapshot.marketId(), snapshot.evaluationCutoff(), duration,
                            exception.getClass().getSimpleName());
                }
            }
        }
    }

    static String idempotencyKey(UUID marketId, Instant cutoff) {
        return "research-lifecycle:v1:" + marketId + ":" + cutoff;
    }
}
