package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.model.AccountBalance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BrokerAccountDto {


    private String broker;

    private String baseCurrency;

    private String accountName;

    private AccountBalanceDto balances;

    private List<Position> openTrades;
}
