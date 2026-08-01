package com.hope.trading.market_intelligence.application.tradeplan;

import java.time.Instant;
import java.util.UUID;

public record RiskValidationAcknowledgment(
        UUID acknowledgmentId, UUID tradePlanId, long acceptedTradePlanVersion,
        long riskValidatedTradePlanVersion, UUID tradingContextId, long tradingContextVersion,
        UUID evaluationId, RiskValidationDecision decision, Instant evaluatedAt,
        Instant acknowledgedAt
) { }
