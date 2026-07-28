package com.hope.trading.trading_core.dashboard.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountEquityService {
    private static final BigDecimal MAX_RELATIVE_DIFFERENCE = new BigDecimal("0.01");

    public AccountEquityResult select(
            BigDecimal balance,
            BigDecimal unrealizedPnl,
            BigDecimal brokerEquity,
            boolean brokerDataStale
    ) {
        BigDecimal calculated = balance.add(unrealizedPnl);
        if (brokerEquity == null || brokerDataStale) {
            return new AccountEquityResult(calculated, calculated, "CALCULATED", false);
        }

        BigDecimal difference = brokerEquity.subtract(calculated).abs();
        BigDecimal reference = calculated.abs().max(BigDecimal.ONE);
        boolean divergent = difference.divide(reference, 8, java.math.RoundingMode.HALF_UP)
                .compareTo(MAX_RELATIVE_DIFFERENCE) > 0;

        return divergent
                ? new AccountEquityResult(calculated, calculated, "CALCULATED", true)
                : new AccountEquityResult(brokerEquity, calculated, "BROKER", false);
    }
}
