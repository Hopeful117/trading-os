package com.hope.trading.trading_core.execution.api.dto;

import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeOutcomeDto(
        UUID id, UUID accountId, UUID brokerAccountId, UUID executionIntentId,
        UUID tradePlanId, long tradePlanVersion, String instrument, String side,
        BigDecimal quantity, BigDecimal entryPrice, BigDecimal stopLossPrice,
        List<BigDecimal> takeProfitPrices, BigDecimal expectedMonetaryRisk,
        BigDecimal riskRewardRatio, BigDecimal realizedPnl, BigDecimal actualFee, Instant closedAt,
        TradeOutcomeStatus status,
        List<TradePlanProvenance> provenance, Instant createdAt, Instant updatedAt) {
    public static TradeOutcomeDto from(TradeOutcome value) {
        return new TradeOutcomeDto(value.id(), value.accountId(), value.brokerAccountId(),
                value.executionIntentId(), value.tradePlanId(), value.tradePlanVersion(),
                value.instrument(), value.side().name(), value.quantity(), value.entryPrice(),
                value.stopLossPrice(), value.takeProfitPrices(), value.expectedMonetaryRisk(),
                value.riskRewardRatio(), value.realizedPnl(), value.actualFee(), value.closedAt(),
                value.status(), value.provenance(), value.createdAt(), value.updatedAt());
    }
}
