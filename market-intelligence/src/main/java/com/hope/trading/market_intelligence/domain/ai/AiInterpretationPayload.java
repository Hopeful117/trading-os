package com.hope.trading.market_intelligence.domain.ai;

public sealed interface AiInterpretationPayload
        permits OpportunityAssessmentResult, OpportunityDiagnosticResult {
    AiInterpretationMode mode();
}
