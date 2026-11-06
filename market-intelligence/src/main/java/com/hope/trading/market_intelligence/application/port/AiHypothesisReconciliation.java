package com.hope.trading.market_intelligence.application.port;

import java.util.List;
import java.util.UUID;

public record AiHypothesisReconciliation(
        String hypothesisId,
        String hypothesis,
        String effect,
        String explanation,
        List<UUID> evidenceReferences
) {
    public AiHypothesisReconciliation {
        evidenceReferences = List.copyOf(evidenceReferences);
    }
}
