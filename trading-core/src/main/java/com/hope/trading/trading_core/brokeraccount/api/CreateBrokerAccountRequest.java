package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBrokerAccountRequest(
        @NotNull BrokerProvider provider,
        @NotBlank @Size(max = 80) String displayName
) {
}
