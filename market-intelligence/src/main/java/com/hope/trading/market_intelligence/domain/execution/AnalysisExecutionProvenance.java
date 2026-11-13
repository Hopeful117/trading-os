package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AnalysisExecutionProvenance(
        UUID marketId,
        AnalysisExecutionMode mode,
        String objective,
        String strategyVersion,
        UUID accountId,
        UUID scanId,
        UUID scanMarketId,
        Instant evaluationCutoff,
        EvidenceAcquisitionPolicy evidencePolicy,
        TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
        ExecutionOrigin executionOrigin,
        String reconstructionRequestFingerprint,
        String historicalEvidenceSelection
) {
    public AnalysisExecutionProvenance(
            UUID marketId, AnalysisExecutionMode mode, String objective, String strategyVersion) {
        this(marketId, mode, objective, strategyVersion, null, null, null, null,
                EvidenceAcquisitionPolicy.LIVE_CURRENT, TradeabilityEvidenceProfile.UNSUPPORTED,
                ExecutionOrigin.CONTEMPORARY_ANALYSIS, null, null);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                null, EvidenceAcquisitionPolicy.LIVE_CURRENT, TradeabilityEvidenceProfile.UNSUPPORTED,
                ExecutionOrigin.CONTEMPORARY_ANALYSIS, null, null);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, TradeabilityEvidenceProfile.UNSUPPORTED,
                ExecutionOrigin.CONTEMPORARY_ANALYSIS, null, null);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, tradeabilityEvidenceProfile,
                ExecutionOrigin.CONTEMPORARY_ANALYSIS, null, null);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy,
            TradeabilityEvidenceProfile tradeabilityEvidenceProfile,
            ExecutionOrigin executionOrigin,
            String reconstructionRequestFingerprint
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, TradeabilityEvidenceProfile.UNSUPPORTED, executionOrigin,
                reconstructionRequestFingerprint, null);
    }

    public AnalysisExecutionProvenance(
            UUID marketId,
            AnalysisExecutionMode mode,
            String objective,
            String strategyVersion,
            UUID accountId,
            UUID scanId,
            UUID scanMarketId,
            Instant evaluationCutoff,
            EvidenceAcquisitionPolicy evidencePolicy,
            ExecutionOrigin executionOrigin,
            String reconstructionRequestFingerprint,
            String historicalEvidenceSelection
    ) {
        this(marketId, mode, objective, strategyVersion, accountId, scanId, scanMarketId,
                evaluationCutoff, evidencePolicy, TradeabilityEvidenceProfile.UNSUPPORTED,
                executionOrigin, reconstructionRequestFingerprint, historicalEvidenceSelection);
    }

    public AnalysisExecutionProvenance {
        Objects.requireNonNull(marketId);
        Objects.requireNonNull(mode);
        objective = objective == null ? "" : objective;
        Objects.requireNonNull(strategyVersion);
        evidencePolicy = evidencePolicy == null
                ? EvidenceAcquisitionPolicy.LIVE_CURRENT : evidencePolicy;
        tradeabilityEvidenceProfile = tradeabilityEvidenceProfile == null
                ? TradeabilityEvidenceProfile.UNSUPPORTED : tradeabilityEvidenceProfile;
        executionOrigin = executionOrigin == null
                ? ExecutionOrigin.CONTEMPORARY_ANALYSIS : executionOrigin;
        if (executionOrigin == ExecutionOrigin.HISTORICAL_RECONSTRUCTION
                && (reconstructionRequestFingerprint == null
                || reconstructionRequestFingerprint.isBlank())) {
            throw new IllegalArgumentException(
                    "Historical reconstruction requires a request fingerprint");
        }
        reconstructionRequestFingerprint = reconstructionRequestFingerprint == null
                ? null : reconstructionRequestFingerprint.trim();
        historicalEvidenceSelection = historicalEvidenceSelection == null
                ? null : historicalEvidenceSelection.trim();
    }
}
