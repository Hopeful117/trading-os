package com.hope.trading.broker_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerAccountDto {
    private String broker;

    private String accountName;

    private String baseCurrency;

    private AccountBalance balances;

    private List<Position> openTrades;
}
