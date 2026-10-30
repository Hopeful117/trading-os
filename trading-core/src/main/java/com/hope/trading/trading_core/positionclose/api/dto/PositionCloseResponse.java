package com.hope.trading.trading_core.positionclose.api.dto;

import java.math.BigDecimal;

public record PositionCloseResponse(
    String commandId,
    String status,
    String externalOrderId,
    String failureReason,
    String resolvedMutationScope,
    String reconciliationResult,
    BigDecimal remainingQuantity
) {}
