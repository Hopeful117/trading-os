package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;

import java.time.Instant;

public record AiStructuredResult(
        AiExecutionReference reference,
        CapabilityAnalysisResult result,
        Instant acceptedAt,
        AiProvenance provenance
) {
    public AiStructuredResult(AiExecutionReference reference, CapabilityAnalysisResult result,
                              Instant acceptedAt) {
        this(reference, result, acceptedAt, null);
    }
}
