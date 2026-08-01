package com.hope.trading.broker_service.broker.api.dto;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class BrokerApiDtos {private BrokerApiDtos(){}
    public record ExecutionApiRequest(@NotNull UUID executionIntentId,@NotNull UUID executionAttemptId,
        @NotBlank @Size(max=200) String idempotencyKey,@NotNull UUID brokerAccountId,
        @NotBlank @Size(max=40) String instrument,@NotNull Side side,@NotNull OrderType orderType,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity,@DecimalMin(value="0",inclusive=false) BigDecimal limitPrice){
        public ExecutionRequest toModel(){return new ExecutionRequest(executionIntentId,executionAttemptId,idempotencyKey,brokerAccountId,instrument,side,orderType,quantity,limitPrice);}}
    public record ReconcileApiRequest(@NotNull UUID executionIntentId,@NotNull UUID executionAttemptId,
        @NotBlank @Size(max=200) String idempotencyKey,@NotNull UUID brokerAccountId){public ReconciliationRequest toModel(){return new ReconciliationRequest(executionIntentId,executionAttemptId,idempotencyKey,brokerAccountId);}}
    public record BrokerApiResponse(String outcome,String externalOrderId,String correlationId,String status,String reasonCode){
        public static BrokerApiResponse from(ExecutionResult r){return switch(r){case Acknowledged a->new BrokerApiResponse("ACKNOWLEDGED",a.externalOrderId(),a.correlationId(),"ACKNOWLEDGED",null);case Rejected x->new BrokerApiResponse("REJECTED",x.externalOrderId(),null,"REJECTED",x.reasonCode());case Unknown x->new BrokerApiResponse("UNKNOWN",null,null,"UNKNOWN",x.reasonCode());};}
        public static BrokerApiResponse from(ReconciliationResult r){return switch(r){case ReconciledOrder x->new BrokerApiResponse("FOUND",x.externalOrderId(),x.correlationId(),x.status().name(),null);case ConfirmedAbsent ignored->new BrokerApiResponse("ABSENT",null,null,null,null);case Inconsistent x->new BrokerApiResponse("INCONSISTENT",null,null,null,x.reasonCode());};}}
}
