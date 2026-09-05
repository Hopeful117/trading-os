package com.hope.trading.trading_core.positionclose.api.dto;

public record PositionCloseResponse(
    String commandId,
    String status,
    String externalOrderId,
    String failureReason,
    String resolvedMutationScope,
    String reconciliationResult
) {}