package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.infrastructure.mapper.*;
import feign.RetryableException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class BrokerExecutionAdapter implements BrokerExecutionPort {
    private final BrokerExecutionClient client;
    private final ExecutionRequestMapper requests=new ExecutionRequestMapper();
    private final ExecutionResponseMapper responses=new ExecutionResponseMapper();
    public BrokerExecutionAdapter(BrokerExecutionClient client){this.client=client;}
    @Override public SubmissionResult submit(ExecutionRequest request){
        try{return responses.submission(client.submit(requests.map(request)));}
        catch(RetryableException exception){return new Unknown("BROKER_OUTCOME_UNKNOWN");}
    }
    @Override public void cancel(UUID accountId,String externalOrderId){
        client.cancel(externalOrderId,accountId);
    }
    @Override public ReconciliationResult reconcile(ReconciliationRequest request){
        try{return responses.reconciliation(client.reconcile(requests.map(request)));}
        catch(RetryableException exception){return new Inconsistent("BROKER_UNAVAILABLE");}
    }
}
