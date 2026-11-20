package com.hope.trading.market_intelligence.domain;

import com.hope.trading.market_intelligence.domain.ai.OpportunityInterpretationMetadata;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.time.Instant;
import java.util.UUID;

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
        String historicalEvidenceSelection,
        TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
        OpportunityInterpretationMetadata interpretationMetadata
) {
    public IntelligenceAnalysisRequest {
        evidencePolicy = evidencePolicy == null
                ? EvidenceAcquisitionPolicy.LIVE_CURRENT : evidencePolicy;
        tradeabilityEvidenceProfile = tradeabilityEvidenceProfile == null
                ? TradeabilityEvidenceProfile.UNSUPPORTED : tradeabilityEvidenceProfile;
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId, Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy, MarketThesis thesis,
            String historicalEvidenceSelection, TradeabilityEvidenceProfile tradeabilityEvidenceProfile) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId, evaluationCutoff,
                evidencePolicy, thesis, historicalEvidenceSelection, tradeabilityEvidenceProfile, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId, Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy, MarketThesis thesis,
            String historicalEvidenceSelection) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, thesis, historicalEvidenceSelection,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective) {
        this(analysisId, marketId, mode, objective, null, null, null, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId, Instant evaluationCutoff) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, EvidenceAcquisitionPolicy.LIVE_CURRENT, null, null,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId, Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, null, null,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest(
            UUID analysisId, UUID marketId, AnalysisExecutionMode mode, String objective,
            UUID accountId, UUID scanId, UUID scanMarketId, Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy, MarketThesis thesis) {
        this(analysisId, marketId, mode, objective, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, thesis, null,
                TradeabilityEvidenceProfile.UNSUPPORTED, null);
    }

    public IntelligenceAnalysisRequest withEvaluationCutoff(Instant cutoff) {
        return copy(cutoff, evidencePolicy(), thesis(), historicalEvidenceSelection(),
                tradeabilityEvidenceProfile(), interpretationMetadata());
    }

    public IntelligenceAnalysisRequest withEvidencePolicy(EvidenceAcquisitionPolicy policy) {
        return copy(evaluationCutoff(), policy, thesis(), historicalEvidenceSelection(),
                tradeabilityEvidenceProfile(), interpretationMetadata());
    }

    public IntelligenceAnalysisRequest withThesis(MarketThesis value) {
        return copy(evaluationCutoff(), evidencePolicy(), value, historicalEvidenceSelection(),
                tradeabilityEvidenceProfile(), interpretationMetadata());
    }

    public IntelligenceAnalysisRequest withHistoricalEvidenceSelection(String selection) {
        return copy(evaluationCutoff(), evidencePolicy(), thesis(), selection,
                tradeabilityEvidenceProfile(), interpretationMetadata());
    }

    public IntelligenceAnalysisRequest withTradeabilityEvidenceProfile(TradeabilityEvidenceProfile profile) {
        return copy(evaluationCutoff(), evidencePolicy(), thesis(), historicalEvidenceSelection(),
                profile, interpretationMetadata());
    }

    public IntelligenceAnalysisRequest withInterpretationMetadata(OpportunityInterpretationMetadata metadata) {
        return copy(evaluationCutoff(), evidencePolicy(), thesis(), historicalEvidenceSelection(),
                tradeabilityEvidenceProfile(), metadata);
    }

    private IntelligenceAnalysisRequest copy(
            Instant cutoff, EvidenceAcquisitionPolicy policy, MarketThesis value,
            String selection, TradeabilityEvidenceProfile profile,
            OpportunityInterpretationMetadata metadata) {
        return new IntelligenceAnalysisRequest(analysisId(), marketId(), mode(), objective(), accountId(), scanId(),
                scanMarketId(), cutoff, policy, value, selection, profile, metadata);
    }
}
