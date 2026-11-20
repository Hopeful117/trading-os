package com.hope.trading.market_intelligence.domain.ai;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** In-process acceptance guard; durable persistence remains a later projection concern. */
public final class AiAnalysisResultLedger {
    private final Set<AiAnalysisIdentity> acceptedIdentities = new HashSet<>();

    public synchronized AiAnalysisResult accept(
            AiAnalysisIdentity expected,
            AiAnalysisResult actual,
            ContextPack contextPack,
            AiAnalysisEligibility eligibility
    ) {
        AiAnalysisResult validated = AiAnalysisResultValidator.requireMatches(
                expected, actual, contextPack, eligibility);
        if (!acceptedIdentities.add(validated.identity())) {
            throw new IllegalArgumentException("AI analysis result already accepted for idempotency identity");
        }
        return validated;
    }
}
