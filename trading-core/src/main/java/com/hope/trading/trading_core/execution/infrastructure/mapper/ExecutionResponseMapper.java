package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient.BrokerResponse;

public final class ExecutionResponseMapper {
    public BrokerExecutionPort.SubmissionResult submission(BrokerResponse value){
        return switch(value.outcome()){
            case "ACKNOWLEDGED" -> new BrokerExecutionPort.Acknowledged(value.externalOrderId(),value.correlationId());
            case "REJECTED" -> new BrokerExecutionPort.Rejected(value.externalOrderId(),value.reasonCode());
            default -> new BrokerExecutionPort.Unknown(value.reasonCode());
        };
    }
    public BrokerExecutionPort.ReconciliationResult reconciliation(BrokerResponse value){
        return switch(value.outcome()){
            case "FOUND" -> new BrokerExecutionPort.ReconciledOrder(value.externalOrderId(),
                    value.correlationId(),BrokerOrderStatus.valueOf(value.status()));
            case "ABSENT" -> new BrokerExecutionPort.ConfirmedAbsent();
            default -> new BrokerExecutionPort.Inconsistent(value.reasonCode());
        };
    }
}
