package com.hope.trading.trading_core.agent.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ResearchScopeRequest(@NotEmpty @Size(min = 2, max = 2) Set<UUID> marketIds,
                                   Instant expiresAt) {
}
