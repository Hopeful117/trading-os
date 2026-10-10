package com.hope.trading.trading_core.execution.infrastructure.mapper;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.persistence.ExecutionIntentEntity;
import com.hope.trading.trading_core.execution.infrastructure.persistence.ExecutionIntentProvenanceEntity;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import java.util.List;
import java.util.Arrays;

public final class ExecutionIntentMapper {
    public ExecutionIntentEntity toEntity(ExecutionIntent value, ExecutionIntentEntity target) {
        target.id=value.id().value();
        target.tradePlanId=value.tradePlan() == null ? null : value.tradePlan().tradePlanId();
        target.tradePlanVersion=value.tradePlan() == null ? null : value.tradePlan().version();
        target.riskEvaluationId=value.riskApproval() == null ? null : value.riskApproval().evaluationId();
        target.riskDecision=value.riskApproval() == null ? null : value.riskApproval().decision().name();
        target.riskApprovedAt=value.riskApproval() == null ? null : value.riskApproval().approvedAt();
        target.riskTradePlanVersion=value.riskApproval() == null ? null : value.riskApproval().evaluatedTradePlanVersion();
        target.purpose=value.purpose().name(); target.targetTradeId=value.targetTradeId().orElse(null);
        target.idempotencyKey=value.idempotencyKey().value();
        target.initiatorId=value.initiatorId(); target.brokerAccountId=value.brokerAccountId();
        target.accountId=value.accountId();
        target.instrument=value.parameters().instrument(); target.side=value.parameters().side().name();
        target.orderType=value.parameters().orderType().name();
        target.quantity=value.parameters().quantity(); target.limitPrice=value.parameters().limitPrice();
        target.stopLossPrice = value.parameters().stopLossPrice();
        target.takeProfitPrices = value.parameters().takeProfitPrices().stream()
                .map(java.math.BigDecimal::toPlainString).collect(java.util.stream.Collectors.joining(","));
        target.expectedMonetaryRisk = value.parameters().expectedMonetaryRisk();
        target.riskRewardRatio = value.parameters().riskRewardRatio();
        target.status=value.status().name();
        target.activeAttemptId=value.activeAttemptId().map(ExecutionAttemptId::value).orElse(null);
        target.createdAt=value.createdAt(); target.updatedAt=value.updatedAt(); target.expiresAt=value.expiresAt();
        target.provenance = value.provenance().stream().map(item -> {
            ExecutionIntentProvenanceEntity result = new ExecutionIntentProvenanceEntity();
            result.opportunityId = item.opportunityId();
            result.opportunityVersion = item.opportunityVersion();
            result.strategyMatchId = item.strategyMatchId();
            result.strategyId = item.strategyId();
            result.strategyVersion = item.strategyVersion();
            result.accountId = item.accountId();
            result.sourceScanId = item.sourceScanId();
            result.sourceScanMarketId = item.sourceScanMarketId();
            result.analysisExecutionId = item.analysisExecutionId();
            result.marketId = item.marketId();
            return result;
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return target;
    }
    public ExecutionIntent toDomain(ExecutionIntentEntity value) {
        var parameters = new ExecutionParameters(value.instrument,
                ExecutionParameters.Side.valueOf(value.side),
                ExecutionParameters.OrderType.valueOf(value.orderType), value.quantity,value.limitPrice,
                value.stopLossPrice,
                value.takeProfitPrices == null || value.takeProfitPrices.isBlank() ? List.of() :
                        Arrays.stream(value.takeProfitPrices.split(",")).map(java.math.BigDecimal::new).toList(),
                value.expectedMonetaryRisk, value.riskRewardRatio);
        if (ExecutionPurpose.valueOf(value.purpose == null ? "ENTRY" : value.purpose) == ExecutionPurpose.EXIT) {
            return ExecutionIntent.rehydrateExit(new ExecutionIntentId(value.id), value.targetTradeId,
                    new IdempotencyKey(value.idempotencyKey), value.initiatorId, value.brokerAccountId,
                    parameters, ExecutionStatus.valueOf(value.status),
                    value.activeAttemptId==null?null:new ExecutionAttemptId(value.activeAttemptId),
                    value.createdAt,value.updatedAt,value.expiresAt,value.version);
        }
        return ExecutionIntent.rehydrate(new ExecutionIntentId(value.id),
                new TradePlanReference(value.tradePlanId, value.tradePlanVersion),
                new RiskApprovalReference(value.riskEvaluationId,
                    RiskApprovalReference.Decision.valueOf(value.riskDecision),value.riskApprovedAt,
                    value.riskTradePlanVersion == null ? 0 : value.riskTradePlanVersion),
                new IdempotencyKey(value.idempotencyKey),value.initiatorId,value.brokerAccountId,value.accountId,
                parameters, value.provenance == null ? List.of() : value.provenance.stream().map(item ->
                        new TradePlanProvenance(item.opportunityId, item.opportunityVersion,
                                item.strategyMatchId, item.strategyId, item.strategyVersion,
                                item.accountId, item.sourceScanId, item.sourceScanMarketId,
                                item.analysisExecutionId, item.marketId)).toList(),
                ExecutionStatus.valueOf(value.status),
                value.activeAttemptId==null?null:new ExecutionAttemptId(value.activeAttemptId),
                value.createdAt,value.updatedAt,value.expiresAt,value.version);
    }
}
