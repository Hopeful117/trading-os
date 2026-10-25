package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketThesisResponse(
        UUID id,
        UUID lineageId,
        long version,
        UUID marketId,
        String instrument,
        MarketThesisState state,
        MarketThesisScenario scenario,
        List<MarketThesisEvidence> evidence,
        List<MarketThesisEvidence> supportingEvidence,
        List<MarketThesisEvidence> contradictingEvidence,
        List<MarketThesisEvidence> missingEvidence,
        List<String> reasons,
        Instant evaluatedAt,
        Instant sourceCutoff,
        Instant validFrom,
        Instant validUntil,
        UUID analysisExecutionId,
        String composerVersion,
        String inputFingerprint,
        UUID supersedes
) {
    public static MarketThesisResponse from(MarketThesis thesis) {
        return new MarketThesisResponse(
                thesis.id(), thesis.lineageId(), thesis.version(), thesis.marketId(),
                thesis.instrument(), thesis.state(), thesis.scenario().orElse(null),
                thesis.evidence(), thesis.supportingEvidence(), thesis.contradictingEvidence(),
                thesis.missingEvidence(), thesis.reasons(), thesis.evaluatedAt(),
                thesis.sourceCutoff(), thesis.validFrom(), thesis.validUntil().orElse(null),
                thesis.analysisExecutionId().orElse(null), thesis.composerVersion(),
                thesis.inputFingerprint(), thesis.supersedes().orElse(null));
    }
}
