package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record InternalOpportunityTradePlanRequest(
        @NotNull UUID actorId,
        @NotNull UUID accountId,
        @NotNull @Valid Context context
) {
    public record Context(
            @NotNull UUID id,
            long version,
            @NotNull Instant capturedAt,
            @NotNull UUID ownerId,
            @NotNull UUID tradingAccountId,
            @NotBlank String accountCurrency,
            @NotNull @Valid RiskBudget riskBudget,
            @NotNull @Valid Preferences preferences
    ) { }

    public record RiskBudget(
            @NotNull BigDecimal amount,
            @NotBlank String currency,
            @NotNull UUID sourceId,
            long sourceVersion
    ) { }

    public record Preferences(
            @NotNull UUID id,
            long version,
            @NotBlank String entryType,
            @NotBlank String stopStrategy,
            @NotNull BigDecimal stopDistancePercent,
            @NotBlank String targetStrategy,
            @NotNull BigDecimal targetRiskMultiple,
            @NotBlank String horizon,
            @NotNull Duration validity
    ) { }
}
