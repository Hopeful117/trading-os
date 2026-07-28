package com.hope.trading.trading_core.broker.dto;

import com.hope.trading.trading_core.dto.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BrokerAccountDto {

    private String brokerAccountId;

    private String broker;

    private String baseCurrency;

    private String accountName;

    private AccountBalanceDto balances;

    private List<Position> openTrades;

    private BigDecimal brokerEquity;

    private Instant dataAt;
}
