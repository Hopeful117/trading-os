package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient.*;

public final class ExecutionRequestMapper {
    public BrokerRequest map(BrokerExecutionPort.ExecutionRequest value){
        var p=value.parameters();
        return new BrokerRequest(value.intentId().value(),value.attemptId().value(),
                value.idempotencyKey().value(),value.brokerAccountId(),p.instrument(),
                p.side().name(),p.orderType().name(),p.quantity(),p.limitPrice());
    }
    public ReconcileRequest map(BrokerExecutionPort.ReconciliationRequest value){
        return new ReconcileRequest(value.intentId().value(),value.attemptId().value(),
                value.idempotencyKey().value(),value.brokerAccountId());
    }
}
