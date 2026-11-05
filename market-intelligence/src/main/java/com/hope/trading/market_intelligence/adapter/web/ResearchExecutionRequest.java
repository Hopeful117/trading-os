package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ResearchExecutionRequest(
        @NotNull @Size(min = 2, max = 2) List<UUID> marketIds,
        @NotBlank @Size(max = 500) String objective,
        @NotBlank @Size(max = 32) String depth
) {
}
