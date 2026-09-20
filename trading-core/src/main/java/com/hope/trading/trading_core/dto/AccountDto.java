package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private UUID accountId;

    private UUID brokerAccountId;

    private String name;

    private String baseCurrency;

    private AccountBalanceDto balances;

    private BigDecimal equity;

    private BigDecimal peakEquity;

    private UUID rulesId;

    private UUID userId;

    private UUID riskProfileId;

    private String riskProfileSemanticVersion;

    private UUID tradePlanningProfileId;

    private Long tradePlanningProfileVersion;

}
