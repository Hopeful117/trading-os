package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationDecision;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RiskValidationAcknowledgmentRequest(
        @NotNull UUID evaluationId,
        @NotNull RiskValidationDecision decision,
        @NotNull Instant evaluatedAt
) { }
