package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateBrokerAccountRequest(
        @NotNull BrokerProvider provider,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull ExecutionMode executionMode,
        BigDecimal initialCapital
        ) {
}
