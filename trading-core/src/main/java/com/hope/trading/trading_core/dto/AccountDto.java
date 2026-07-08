package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private UUID accountId;

    private String name;

    private String baseCurrency;

    private List<AccountBalanceDto> balances;

    private BigDecimal equity;

    private BigDecimal peakEquity;

    private UUID rulesId;

    private UUID userId;
}
