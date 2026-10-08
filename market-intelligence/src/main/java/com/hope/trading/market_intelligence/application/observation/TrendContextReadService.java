package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState;
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
    private final CapabilityExecutionRepository capabilityExecutions;
    private final Clock clock;

    public TrendContextReadService(
            ObservationRepository observations,
            AnalysisExecutionRepository executions,
            CapabilityExecutionRepository capabilityExecutions,
            Clock clock) {
        this.observations = observations;
        this.executions = executions;
        this.capabilityExecutions = capabilityExecutions;
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
        boolean current = valid && execution.filter(this::isCompleted)
                .map(value -> producedBy(latestObservation, value))
                .orElse(false);
        String operationalStatus = operationalStatus(execution, valid, latestObservation, current);
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

    private boolean producedBy(Observation observation, AnalysisExecution execution) {
        var capabilityExecutionIds = capabilityExecutions
                .findByAnalysisExecutionId(execution.executionId())
                .stream()
                .filter(value -> value.state() == CapabilityExecutionState.COMPLETED)
                .filter(value -> value.capabilityId().value()
                        .equals(TrendContextAnalysisCapability.CAPABILITY_ID))
                .map(CapabilityExecution::id)
                .collect(java.util.stream.Collectors.toSet());
        return observation.evidence().stream()
                .anyMatch(value -> capabilityExecutionIds.contains(
                        value.capabilityResult().capabilityExecutionId()));
    }

    private String operationalStatus(
            Optional<AnalysisExecution> execution, boolean valid, Observation observation,
            boolean current) {
        if (execution.isPresent()) {
            AnalysisExecutionStatus status = execution.get().status();
            if (status == AnalysisExecutionStatus.COMPLETED) {
                if (!current) return valid ? "STALE" : "UNAVAILABLE";
                return payload(observation).map(value -> value.content().operationalStatus())
                        .orElse("UNAVAILABLE");
            }
            if (status == AnalysisExecutionStatus.FAILED
                    || status == AnalysisExecutionStatus.EXPIRED
                    || status == AnalysisExecutionStatus.CANCELLED) {
                return "UNAVAILABLE";
            }
            return "IN_PROGRESS";
        }
        if (observation == null) {
            return "MISSING";
        }
        return valid ? payload(observation)
                .map(value -> value.content().operationalStatus()).orElse("UNAVAILABLE") : "STALE";
    }

    private String validity(Observation observation, Instant now, boolean current) {
        if (observation == null) return "NONE";
        if (!current) return observation.validUntil().filter(now::isBefore).isPresent()
                ? "HISTORICAL" : "EXPIRED";
        return "VALID";
    }
}
