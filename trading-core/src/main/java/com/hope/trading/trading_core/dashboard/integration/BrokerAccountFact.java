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
        boolean balancesAvailable,
        BigDecimal brokerEquity,
        boolean brokerEquityTotal,
        List<PositionFact> positions,
        Instant dataAt,
        PositionPnlTreatment positionPnlTreatment
) {
    public BrokerAccountFact {
        balances = balances == null ? Map.of() : Map.copyOf(balances);
        positions = positions == null ? List.of() : List.copyOf(positions);
        positionPnlTreatment = positionPnlTreatment == null
                ? PositionPnlTreatment.ADDITIVE : positionPnlTreatment;
    }

    public BrokerAccountFact(String accountId, String broker, String currency,
                             Map<String, BigDecimal> balances, BigDecimal brokerEquity,
                             List<PositionFact> positions, Instant dataAt,
                             PositionPnlTreatment positionPnlTreatment) {
        this(accountId, broker, currency, balances, true, brokerEquity, false,
                positions, dataAt, positionPnlTreatment);
    }
}
