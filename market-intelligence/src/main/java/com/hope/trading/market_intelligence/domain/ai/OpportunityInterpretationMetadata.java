package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;

import java.util.Objects;

/** Provider-neutral identity carried by an opportunity interpretation execution. */
public record OpportunityInterpretationMetadata(
        OpportunityVersionReference opportunity,
        AiInterpretationMode mode,
        ContextPackId contextPackId,
        ArtifactFingerprint contextPackFingerprint
) {
    public OpportunityInterpretationMetadata {
        Objects.requireNonNull(opportunity, "opportunity");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(contextPackId, "contextPackId");
        Objects.requireNonNull(contextPackFingerprint, "contextPackFingerprint");
    }
}
