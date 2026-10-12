package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;

public record CreateBrokerAccountRequest(
        @NotNull BrokerProvider provider,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull ExecutionMode executionMode,
        BigDecimal initialCapital,
        @Valid RiskProfileReference riskProfile,
        SettlementModel settlementModel,
        String riskTimeZone,
        String riskDayResetTime
        ) {
    private static final String DEFAULT_RISK_DAY_RESET_TIME = "00:00";

    public CreateBrokerAccountRequest {
        riskTimeZone = riskTimeZone == null || riskTimeZone.isBlank() ? "UTC" : riskTimeZone.strip();
        riskDayResetTime = riskDayResetTime == null || riskDayResetTime.isBlank()
                ? DEFAULT_RISK_DAY_RESET_TIME : riskDayResetTime.strip();
    }

    public CreateBrokerAccountRequest(BrokerProvider provider, String displayName, ExecutionMode executionMode,
                                      BigDecimal initialCapital, RiskProfileReference riskProfile) {
        this(provider, displayName, executionMode, initialCapital, riskProfile, null, "UTC",
                DEFAULT_RISK_DAY_RESET_TIME);
    }

    public CreateBrokerAccountRequest(BrokerProvider provider, String displayName, ExecutionMode executionMode,
                                      BigDecimal initialCapital, RiskProfileReference riskProfile,
                                      SettlementModel settlementModel) {
        this(provider, displayName, executionMode, initialCapital, riskProfile, settlementModel, "UTC",
                DEFAULT_RISK_DAY_RESET_TIME);
    }
}
