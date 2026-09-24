package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
public class TrendContextReadService {
    private final ObservationRepository observations;
    private final AnalysisExecutionRepository executions;
    private final Clock clock;

    public TrendContextReadService(
            ObservationRepository observations,
            AnalysisExecutionRepository executions,
            Clock clock) {
        this.observations = observations;
        this.executions = executions;
        this.clock = clock;
    }

    public TrendContextReadModel find(UUID marketId) {
        Instant now = clock.instant();
        Optional<Observation> latest = observations.findByType(new ObservationType("TREND_CONTEXT"))
                .stream()
                .filter(value -> payload(value)
                        .map(item -> item.content().assessment().marketId().equals(marketId))
                        .orElse(false))
                .max(Comparator.comparing(Observation::createdAt));
        Optional<AnalysisExecution> execution = executions.findLatestByMarketId(marketId)
                .filter(value -> value.capabilities().contains(TrendContextAnalysisCapability.CAPABILITY_ID));

        TrendContextObservationPayload lastPayload = latest.flatMap(this::payload).orElse(null);
        Observation latestObservation = latest.orElse(null);
        boolean valid = latestObservation != null
                && latestObservation.status() == ObservationStatus.ACTIVE
                && latestObservation.validUntil().map(now::isBefore).orElse(true);
        boolean current = valid && execution.map(this::isCompleted).orElse(true)
                && execution.flatMap(AnalysisExecution::completedAt)
                .map(completed -> !completed.isAfter(latestObservation.createdAt()))
                .orElse(execution.isEmpty());
        String operationalStatus = operationalStatus(execution, valid, latestObservation);
        return new TrendContextReadModel(
                marketId,
                operationalStatus,
                current,
                validity(latestObservation, now, current),
                latestObservation == null ? null : latestObservation.id(),
                latestObservation == null ? null : latestObservation.lineageId(),
                latestObservation == null ? null : latestObservation.version(),
                latestObservation == null ? null : latestObservation.status(),
                latestObservation == null ? null : latestObservation.validFrom(),
                latestObservation == null ? null : latestObservation.validUntil().orElse(null),
                current && lastPayload != null ? lastPayload.content().assessment() : null,
                lastPayload == null ? null : lastPayload.content().assessment());
    }

    private Optional<TrendContextObservationPayload> payload(Observation observation) {
        return observation.payload()
                .filter(TrendContextObservationPayload.class::isInstance)
                .map(TrendContextObservationPayload.class::cast);
    }

    private boolean isCompleted(AnalysisExecution execution) {
        return execution.status() == AnalysisExecutionStatus.COMPLETED;
    }

    private String operationalStatus(
            Optional<AnalysisExecution> execution, boolean valid, Observation observation) {
        if (execution.isPresent()) {
            AnalysisExecutionStatus status = execution.get().status();
            if (status == AnalysisExecutionStatus.COMPLETED) {
                return valid ? "AVAILABLE" : "STALE";
            }
            if (status == AnalysisExecutionStatus.FAILED
                    || status == AnalysisExecutionStatus.EXPIRED
                    || status == AnalysisExecutionStatus.CANCELLED) {
                return "UNAVAILABLE";
            }
            return "IN_PROGRESS";
        }
        return observation == null ? "MISSING" : (valid ? "AVAILABLE" : "STALE");
    }

    private String validity(Observation observation, Instant now, boolean current) {
        if (observation == null) return "NONE";
        if (!current) return observation.validUntil().filter(now::isBefore).isPresent()
                ? "HISTORICAL" : "EXPIRED";
        return "VALID";
    }
}
