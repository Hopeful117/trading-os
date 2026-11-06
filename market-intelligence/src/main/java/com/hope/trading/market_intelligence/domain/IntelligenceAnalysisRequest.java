package com.hope.trading.market_intelligence.domain;

import java.util.UUID;
import java.time.Instant;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

public record IntelligenceAnalysisRequest(
        UUID analysisId,
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy,
        MarketThesis thesis,
        String historicalEvidenceSelection
) {
    public IntelligenceAnalysisRequest {
        evidencePolicy = evidencePolicy == null
                ? EvidenceAcquisitionPolicy.LIVE_CURRENT : evidencePolicy;
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(analysisId, marketId, mode, objective, null, null, null, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null);
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
                accountId, scanId, scanMarketId, null, EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null);
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
                evaluationCutoff, EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null);
    }

    public IntelligenceAnalysisRequest(
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
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, null, null);
    }

    public IntelligenceAnalysisRequest withEvaluationCutoff(Instant cutoff) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), cutoff, evidencePolicy(), thesis(), historicalEvidenceSelection());
    }

    public IntelligenceAnalysisRequest withEvidencePolicy(EvidenceAcquisitionPolicy policy) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), evaluationCutoff(), policy, thesis(), historicalEvidenceSelection());
    }

    public IntelligenceAnalysisRequest withThesis(MarketThesis value) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(), scanMarketId(),
                evaluationCutoff(), evidencePolicy(), value, historicalEvidenceSelection());
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId,
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy,
            MarketThesis thesis
    ) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, thesis, null);
    }

    public IntelligenceAnalysisRequest withHistoricalEvidenceSelection(String selection) {
        return new IntelligenceAnalysisRequest(
                analysisId(), marketId(), mode(), objective(), accountId(), scanId(), scanMarketId(),
                evaluationCutoff(), evidencePolicy(), thesis(), selection);
    }
}
