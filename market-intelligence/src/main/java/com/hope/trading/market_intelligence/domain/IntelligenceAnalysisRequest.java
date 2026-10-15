package com.hope.trading.market_intelligence.domain;

import java.util.UUID;
import java.time.Instant;

public record IntelligenceAnalysisRequest(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy
) {
    public IntelligenceAnalysisRequest {
        evidencePolicy = evidencePolicy == null
                ? EvidenceAcquisitionPolicy.LIVE_CURRENT : evidencePolicy;
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(analysisId, marketId, mode, objective, null, null, null, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId,
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId
    ) {
        this(analysisId, marketId, mode, objective,
                accountId, scanId, scanMarketId, null, EvidenceAcquisitionPolicy.LIVE_CURRENT);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId,
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff
    ) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, EvidenceAcquisitionPolicy.LIVE_CURRENT);
    }

    public IntelligenceAnalysisRequest withEvaluationCutoff(Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), cutoff, evidencePolicy());
    }

    public IntelligenceAnalysisRequest withEvidencePolicy(EvidenceAcquisitionPolicy policy) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), evaluationCutoff(), policy);
    }
}
