package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState;
import com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;
import com.hope.trading.market_intelligence.domain.observation.TrendContextObservationPayload;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextAssessment;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.SemanticInputType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendContextEvidenceSelectorTest {
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");
    private static final UUID ANALYSIS = UUID.randomUUID();
    private static final UUID MARKET = UUID.randomUUID();

    private final ObservationRepository observations = mock(ObservationRepository.class);
    private final CapabilityExecutionRepository executions = mock(CapabilityExecutionRepository.class);
    private final TrendContextEvidenceSelector selector =
            new TrendContextEvidenceSelector(observations, executions);

    @Test
    void selectsOnlyObservationEvidenceProducedByTheRequestedAnalysisExecution() {
        UUID currentCapabilityExecution = UUID.randomUUID();
        CapabilityExecution execution = mock(CapabilityExecution.class);
        when(execution.id()).thenReturn(currentCapabilityExecution);
        when(execution.state()).thenReturn(CapabilityExecutionState.COMPLETED);
        when(execution.capabilityId()).thenReturn(new com.hope.trading.market_intelligence.domain.capability.CapabilityId(
                com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability.CAPABILITY_ID));
        when(executions.findByAnalysisExecutionId(ANALYSIS)).thenReturn(List.of(execution));

        Observation unrelated = observation(UUID.randomUUID());
        Observation selected = observation(currentCapabilityExecution);
        when(observations.findByType(new ObservationType("TREND_CONTEXT")))
                .thenReturn(List.of(unrelated, selected));

        assertThat(selector.select(ANALYSIS, MARKET,
                List.of(new RequiredSemanticInput(SemanticInputType.OBSERVATION,
                        "TREND_CONTEXT_DIRECTION")), NOW))
                .contains(selected);
    }

    private Observation observation(UUID capabilityExecutionId) {
        Observation observation = mock(Observation.class);
        when(observation.status()).thenReturn(ObservationStatus.ACTIVE);
        when(observation.createdAt()).thenReturn(NOW);
        when(observation.validUntil()).thenReturn(Optional.empty());
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.marketId()).thenReturn(MARKET);
        TrendContextCapabilityContent content = mock(TrendContextCapabilityContent.class);
        when(content.assessment()).thenReturn(assessment);
        when(content.cutOffAt()).thenReturn(NOW);
        TrendContextObservationPayload payload = mock(TrendContextObservationPayload.class);
        when(payload.content()).thenReturn(content);
        when(observation.payload()).thenReturn(Optional.of(payload));
        when(observation.evidence()).thenReturn(List.of(new ObservationEvidence(
                UUID.randomUUID(), "trend-context", "context", "context",
                Map.of(), Map.of(), NOW, BigDecimal.ONE,
                new CapabilityResultTrace(capabilityExecutionId, "trend-context", "v1", List.of(
                        mock(com.hope.trading.market_intelligence.domain.observation.ArtifactTrace.class))))));
        return observation;
    }
}
