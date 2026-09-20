package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExecutionReadinessRequest(@NotNull UUID evaluationId) {
}
