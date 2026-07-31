package com.hope.trading.trading_core.execution.domain.valueobject;

public enum BrokerOrderStatus {
    ACKNOWLEDGED, REJECTED, PARTIALLY_FILLED, FILLED, CANCELLED, UNKNOWN;
    public boolean terminal() {
        return this == REJECTED || this == FILLED || this == CANCELLED;
    }
}
