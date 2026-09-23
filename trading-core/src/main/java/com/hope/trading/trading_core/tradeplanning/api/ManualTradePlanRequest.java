package com.hope.trading.trading_core.tradeplanning.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ManualTradePlanRequest(
        @NotNull UUID accountId,
        @NotNull UUID marketId,
        @NotBlank String direction,
        @NotBlank String entryType,
        BigDecimal entryPrice,
        @NotNull @Positive BigDecimal referencePrice,
        @NotNull @Positive BigDecimal stopLoss,
        @NotBlank String stopRationale,
        @NotEmpty List<@Valid TakeProfit> takeProfits,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal monetaryRisk,
        @NotBlank String thesis,
        @NotEmpty Set<String> confirmationConditions,
        @NotEmpty Set<String> invalidationConditions,
        Set<String> managementRules
) {
    public record TakeProfit(
            @NotNull @Positive BigDecimal price,
            @NotNull @Positive BigDecimal allocationPercent
    ) { }
}
