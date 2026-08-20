package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ActiveScanScopeResolutionRequestDto(
        @NotNull UUID accountId,
        @Size(max = 500) String objective,
        List<UUID> requestedMarketIds
) {
}
