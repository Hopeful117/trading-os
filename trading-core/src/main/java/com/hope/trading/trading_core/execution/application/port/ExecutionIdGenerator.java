package com.hope.trading.trading_core.execution.application.port;

import com.hope.trading.trading_core.execution.domain.valueobject.*;

public interface ExecutionIdGenerator {
    ExecutionIntentId nextIntentId();
    ExecutionAttemptId nextAttemptId();
    BrokerOrderId nextBrokerOrderId();
}
