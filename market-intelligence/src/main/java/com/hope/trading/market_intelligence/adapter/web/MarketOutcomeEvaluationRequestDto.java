package com.hope.trading.market_intelligence.adapter.web;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record MarketOutcomeEvaluationRequestDto(
        UUID marketId,
        Instant evaluationCutoff,
        Duration horizon,
        UUID analysisExecutionId
) { }
