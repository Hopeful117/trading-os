package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

public record ExecutionDto(
        UUID id,UUID tradePlanId,long tradePlanVersion,UUID riskEvaluationId,
        String idempotencyKey,UUID brokerAccountId,ExecutionStatus status,
        Instant createdAt,Instant updatedAt,Instant expiresAt,long version,
        String brokerExternalOrderId,String brokerOrderStatus,
        BigDecimal filledQuantity,BigDecimal averageFillPrice,BigDecimal totalFees,
        String failureReason
) {
    public static ExecutionDto from(ExecutionIntent value){
        return new ExecutionDto(value.id().value(),value.tradePlan().tradePlanId(),
                value.tradePlan().version(),value.riskApproval().evaluationId(),
                value.idempotencyKey().value(),value.brokerAccountId(),value.status(),
                value.createdAt(),value.updatedAt(),value.expiresAt(),value.version(),
                null,null,null,null,null,null);
    }
    public static ExecutionDto from(ExecutionIntent intent,
            Optional<BrokerOrder> brokerOrder,Optional<ExecutionAttempt> latestAttempt){
        String extId=null;String brokerStatus=null;
        BigDecimal filledQty=null;BigDecimal avgPrice=null;BigDecimal fees=null;
        if(brokerOrder.isPresent()){
            BrokerOrder order=brokerOrder.get();
            extId=order.externalOrderId();brokerStatus=order.status().name();
            if(!order.fills().isEmpty()){
                BigDecimal totalQty=BigDecimal.ZERO;BigDecimal weightedPrice=BigDecimal.ZERO;
                BigDecimal totalFees=BigDecimal.ZERO;
                for(BrokerOrder.Fill f:order.fills()){
                    totalQty=totalQty.add(f.quantity());
                    weightedPrice=weightedPrice.add(f.price().multiply(f.quantity()));
                    totalFees=totalFees.add(f.fee());
                }
                filledQty=totalQty;
                avgPrice=totalQty.signum()>0?weightedPrice.divide(totalQty,12,RoundingMode.HALF_UP):null;
                fees=totalFees;
            }
        }
        String failReason=latestAttempt.map(a->mapFailureReason(a.resultCode(),intent.status())).orElse(null);
        return new ExecutionDto(intent.id().value(),intent.tradePlan().tradePlanId(),
                intent.tradePlan().version(),intent.riskApproval().evaluationId(),
                intent.idempotencyKey().value(),intent.brokerAccountId(),intent.status(),
                intent.createdAt(),intent.updatedAt(),intent.expiresAt(),intent.version(),
                extId,brokerStatus,filledQty,avgPrice,fees,failReason);
    }
    private static String mapFailureReason(String resultCode,ExecutionStatus status){
        if(resultCode==null)return null;
        return switch(resultCode){
            case "ACKNOWLEDGED"->null;
            case "REJECTED"->"Order rejected by broker";
            case "TIMEOUT"->"Submission timed out";
            case "OUTCOME_UNKNOWN"->"Submission outcome uncertain";
            default->"Execution failed";
        };
    }
}
