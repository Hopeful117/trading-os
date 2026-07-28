package com.hope.trading.trading_core.dashboard.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BrokerAccountFact(
        String accountId,
        String broker,
        String currency,
        Map<String, BigDecimal> balances,
        BigDecimal brokerEquity,
        List<BrokerPositionFact> positions,
        Instant dataAt
) {
    public BrokerAccountFact {
        balances = Map.copyOf(balances);
        positions = List.copyOf(positions);
    }
}
