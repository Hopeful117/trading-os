package com.hope.trading.trading_core.positionclose.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PositionCloseRequest(
    @Size(max = 200) String brokerPositionReference,
    UUID tradeId
) {
    public PositionCloseRequest(String brokerPositionReference) {
        this(brokerPositionReference, null);
    }
}
