package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record OpportunityInterpretationRequest(
        @NotNull UUID opportunityId,
        @Min(1) long opportunityVersion,
        Instant evaluationCutoff
) {
}
