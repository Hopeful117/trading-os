package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TrendContextObservationService {
    private final CapabilityExecutionRepository executions;
    private final ObservationBuilder observations;

    public TrendContextObservationService(
            CapabilityExecutionRepository executions, ObservationBuilder observations) {
        this.executions = executions;
        this.observations = observations;
    }

    public void buildIfAssessmentExists(UUID analysisExecutionId, String instrument) {
        boolean assessmentExists = executions.findByAnalysisExecutionId(analysisExecutionId).stream()
                .filter(value -> value.capabilityId().value()
                        .equals(TrendContextAnalysisCapability.CAPABILITY_ID))
                .filter(value -> value.state() == CapabilityExecutionState.COMPLETED)
                .flatMap(value -> value.result().stream())
                .flatMap(value -> value.artifacts().stream())
                .anyMatch(value -> value.artifact().content()
                        instanceof com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent content
                        && content.assessment() != null);
        if (assessmentExists) {
            observations.build(analysisExecutionId, instrument,
                    new TrendContextObservationRule());
        }
    }
}
