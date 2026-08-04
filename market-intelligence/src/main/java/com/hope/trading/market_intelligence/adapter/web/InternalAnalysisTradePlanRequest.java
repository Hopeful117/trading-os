package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InternalAnalysisTradePlanRequest(
        @NotNull UUID actorId,
        @NotNull UUID accountId,
        @NotNull @Valid Context context
) {
    public record Context(
            @NotNull UUID id,
            @Min(1) long version,
            @NotNull Instant capturedAt,
            @NotNull UUID ownerId,
            @NotNull UUID tradingAccountId,
            @NotBlank String accountCurrency,
            @NotNull @Valid RiskBudget riskBudget,
            @NotNull @Valid Preferences preferences
    ) { }

    public record RiskBudget(
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotNull UUID sourceId,
            @Min(1) long sourceVersion
    ) { }

    public record Preferences(
            @NotNull UUID id,
            @Min(1) long version,
            @NotBlank String entryType,
            @NotBlank String stopStrategy,
            @NotNull @Positive BigDecimal stopDistancePercent,
            @NotBlank String targetStrategy,
            @NotNull @Positive BigDecimal targetRiskMultiple,
            @NotBlank String horizon,
            @NotNull java.time.Duration validity
    ) { }
}
