package com.hope.trading.trading_core.agent.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ResearchScopeRequest(@NotNull UUID brokerAccountId,
                                   Instant expiresAt,
                                   HistoricalResearchConstraintsRequest historicalConstraints) {
}
