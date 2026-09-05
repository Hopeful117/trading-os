package com.hope.trading.trading_core.positionclose.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionCloseRequest(
    @NotBlank @Size(max = 200) String brokerPositionReference
) {}