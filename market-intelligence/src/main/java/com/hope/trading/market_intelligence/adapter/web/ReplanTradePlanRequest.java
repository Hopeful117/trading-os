package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ReplanTradePlanRequest(
        @NotNull UUID actorId, @NotNull @Positive BigDecimal marketPrice,
        String reason
) {}
