package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionHistoryItemDto(
        UUID outcomeId,
        UUID executionIntentId,
        UUID accountId,
        String accountName,
        String origin,
        List<TradePlanProvenance> provenance,
        UUID tradeId,
        UUID tradePlanId,
        long tradePlanVersion,
        UUID riskEvaluationId,
        String instrument,
        String side,
        BigDecimal quantity,
        BigDecimal plannedEntryPrice,
        BigDecimal stopLossPrice,
        BigDecimal expectedMonetaryRisk,
        BigDecimal riskRewardRatio,
        BigDecimal actualEntryPrice,
        BigDecimal realizedPnl,
        BigDecimal actualFee,
        TradeOutcomeStatus outcomeStatus,
        ExecutionStatus executionStatus,
        TradeStatus tradeStatus,
        Instant openedAt,
        Instant closedAt,
        boolean lineageAvailable,
        boolean resultAvailable,
        UUID closeExecutionIntentId,
        ExecutionStatus closeExecutionStatus,
        Instant createdAt) {
}
