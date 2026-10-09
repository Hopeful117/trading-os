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
        return select(new AccountValuationResult(balance, "COMPLETE", null, null, null),
                unrealizedPnl, brokerEquity, true, brokerDataStale);
    }

    public AccountEquityResult select(
            AccountValuationResult valuation,
            BigDecimal unrealizedPnl,
            BigDecimal brokerEquity,
            boolean brokerDataStale
    ) {
        return select(valuation, unrealizedPnl, brokerEquity, true, brokerDataStale);
    }

    public AccountEquityResult select(
            AccountValuationResult valuation,
            BigDecimal unrealizedPnl,
            BigDecimal brokerEquity,
            boolean brokerEquityTotal,
            boolean brokerDataStale
    ) {
        BigDecimal calculated = valuation.value() == null || unrealizedPnl == null
                ? null : valuation.value().add(unrealizedPnl);
        if (brokerEquity == null || !brokerEquityTotal || brokerDataStale) {
            return new AccountEquityResult(calculated, calculated,
                    calculated == null ? "UNAVAILABLE" : "CALCULATED", false,
                    valuation.status(), valuation.valuationTimestamp(), valuation.policyVersion());
        }

        if (calculated == null) {
            return new AccountEquityResult(null, null, "UNAVAILABLE", false,
                    valuation.status(), valuation.valuationTimestamp(), valuation.policyVersion());
        }
        BigDecimal difference = brokerEquity.subtract(calculated).abs();
        BigDecimal reference = calculated.abs().max(BigDecimal.ONE);
        boolean divergent = difference.divide(reference, 8, java.math.RoundingMode.HALF_UP)
                .compareTo(MAX_RELATIVE_DIFFERENCE) > 0;

        return divergent
                ? new AccountEquityResult(calculated, calculated, "CALCULATED", true,
                valuation.status(), valuation.valuationTimestamp(), valuation.policyVersion())
                : new AccountEquityResult(brokerEquity, calculated, "BROKER", false,
                valuation.status(), valuation.valuationTimestamp(), valuation.policyVersion());
    }
}
