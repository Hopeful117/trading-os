package com.hope.trading.market_intelligence.adapter.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public record CreateTradePlanRequest(
        @NotEmpty Set<UUID> opportunityIds,
        @NotNull UUID tradingContextId,
        @Positive long contextVersion,
        @NotNull UUID actorId,
        @NotNull @Positive BigDecimal marketPrice
) {}
