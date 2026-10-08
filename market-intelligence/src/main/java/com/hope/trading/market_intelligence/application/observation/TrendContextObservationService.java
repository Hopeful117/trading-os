package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecutionState;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TrendContextObservationService {
    private final CapabilityExecutionRepository executions;
    private final ObservationBuilder observations;
    private final AnalysisExecutionRepository analysisExecutions;

    public TrendContextObservationService(
            CapabilityExecutionRepository executions, ObservationBuilder observations) {
        this(executions, observations, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TrendContextObservationService(
            CapabilityExecutionRepository executions,
            ObservationBuilder observations,
            AnalysisExecutionRepository analysisExecutions) {
        this.executions = executions;
        this.observations = observations;
        this.analysisExecutions = analysisExecutions;
    }

    public void buildIfAssessmentExists(UUID analysisExecutionId, String instrument) {
        if (terminal(analysisExecutionId)) {
            return;
        }
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
            // Recheck immediately before persistence so terminal lifecycle updates do not promote late results.
            if (terminal(analysisExecutionId)) return;
            observations.build(analysisExecutionId, instrument,
                    new TrendContextObservationRule());
        }
    }

    private boolean terminal(UUID analysisExecutionId) {
        return analysisExecutions != null && analysisExecutions.findById(analysisExecutionId)
                .map(execution -> execution.status() == AnalysisExecutionStatus.CANCELLED
                        || execution.status() == AnalysisExecutionStatus.EXPIRED
                        || execution.status() == AnalysisExecutionStatus.FAILED)
                .orElse(true);
    }
}
