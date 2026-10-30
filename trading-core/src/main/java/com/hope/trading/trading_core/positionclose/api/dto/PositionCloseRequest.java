package com.hope.trading.trading_core.positionclose.api.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.UUID;

public record PositionCloseRequest(
    @Size(max = 200) String brokerPositionReference,
    UUID tradeId,
    @DecimalMin(value = "0.000000000001", inclusive = true) BigDecimal closeQuantity
) {
    public PositionCloseRequest(String brokerPositionReference) {
        this(brokerPositionReference, null, null);
    }

    public PositionCloseRequest(String brokerPositionReference, UUID tradeId) {
        this(brokerPositionReference, tradeId, null);
    }
}
