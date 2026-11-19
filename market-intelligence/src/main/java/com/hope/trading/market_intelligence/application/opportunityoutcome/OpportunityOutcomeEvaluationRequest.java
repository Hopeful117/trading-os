package com.hope.trading.market_intelligence.application.opportunityoutcome;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OpportunityOutcomeEvaluationRequest(
        OpportunityId opportunityId,
        OpportunityVersion opportunityVersion,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        UUID analysisExecutionId,
        String historicalEvidenceSelection
) {
    public OpportunityOutcomeEvaluationRequest {
        Objects.requireNonNull(opportunityId, "opportunityId");
        Objects.requireNonNull(opportunityVersion, "opportunityVersion");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff");
        Objects.requireNonNull(horizon, "horizon");
    }

    public OpportunityOutcomeEvaluationRequest(
            OpportunityId opportunityId,
            OpportunityVersion opportunityVersion,
            Instant evaluationCutoff,
            OutcomeHorizon horizon,
            UUID analysisExecutionId
    ) {
        this(opportunityId, opportunityVersion, evaluationCutoff, horizon,
                analysisExecutionId, null);
    }
}
