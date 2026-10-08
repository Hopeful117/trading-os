package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextTimeframeAssessment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Selects only current, typed Trend Context evidence for strategy evaluation. */
@Component
public class TrendContextEvidenceSelector {
    private final ObservationRepository observations;
    private final CapabilityExecutionRepository executions;

    public TrendContextEvidenceSelector(
            ObservationRepository observations,
            CapabilityExecutionRepository executions) {
        this.observations = observations;
        this.executions = executions;
    }

    public boolean requiresTrendContext(Collection<RequiredSemanticInput> inputs) {
        return inputs.stream().anyMatch(input -> input.key().startsWith("TREND_CONTEXT_"));
    }

    public Optional<Observation> select(
            UUID analysisExecutionId,
            UUID marketId,
            Collection<RequiredSemanticInput> inputs,
            Instant evaluatedAt) {
        if (!requiresTrendContext(inputs)) {
            return Optional.empty();
        }
        var trendContextExecutionIds = executions.findByAnalysisExecutionId(analysisExecutionId)
                .stream()
                .filter(execution -> execution.state() == CapabilityExecutionState.COMPLETED)
                .filter(execution -> execution.capabilityId().value()
                        .equals(TrendContextAnalysisCapability.CAPABILITY_ID))
                .map(CapabilityExecution::id)
                .collect(java.util.stream.Collectors.toSet());
        if (trendContextExecutionIds.isEmpty()) return Optional.empty();
        return observations.findByType(new com.hope.trading.market_intelligence.domain.observation.ObservationType("TREND_CONTEXT"))
                .stream()
                .filter(observation -> observation.status() == ObservationStatus.ACTIVE)
                .filter(observation -> observation.evidence().stream()
                        .anyMatch(evidence -> trendContextExecutionIds.contains(
                                evidence.capabilityResult().capabilityExecutionId())))
                .filter(observation -> observation.payload()
                        .filter(TrendContextObservationPayload.class::isInstance)
                        .map(TrendContextObservationPayload.class::cast)
                        .map(payload -> payload.content().assessment() != null
                                && payload.content().assessment().marketId().equals(marketId)
                                && eligible(payload.content()))
                        .orElse(false))
                .filter(observation -> observation.validUntil()
                        .map(evaluatedAt::isBefore).orElse(true))
                .filter(observation -> observation.payload()
                        .filter(TrendContextObservationPayload.class::isInstance)
                        .map(TrendContextObservationPayload.class::cast)
                        .map(payload -> !payload.content().cutOffAt().isAfter(evaluatedAt))
                        .orElse(false))
                .max(Comparator.comparing(Observation::createdAt));
    }

    private boolean eligible(TrendContextCapabilityContent content) {
        if (!"AVAILABLE".equalsIgnoreCase(content.operationalStatus())
                || content.assessment() == null) {
            return false;
        }
        return requiredRoleIsFresh(content, TrendContextRole.BIAS)
                && requiredRoleIsFresh(content, TrendContextRole.SETUP);
    }

    private boolean requiredRoleIsFresh(
            TrendContextCapabilityContent content, TrendContextRole role) {
        TrendContextTimeframeAssessment timeframe = content.assessment().roleAssessments().get(role);
        return timeframe != null
                && timeframe.fresh()
                && content.sourceReferences().containsKey(role);
    }
}
