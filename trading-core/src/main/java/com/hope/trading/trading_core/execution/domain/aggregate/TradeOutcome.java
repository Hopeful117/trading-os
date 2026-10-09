package com.hope.trading.trading_core.execution.domain.aggregate;

import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.shared.domain.model.TradePlanProvenance;
import com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TradeOutcome {
    private final UUID id;
    private final UUID accountId;
    private final UUID brokerAccountId;
    private final UUID executionIntentId;
    private final UUID tradePlanId;
    private final long tradePlanVersion;
    private final List<TradePlanProvenance> provenance;
    private final String instrument;
    private final ExecutionParameters.Side side;
    private final BigDecimal quantity;
    private final BigDecimal entryPrice;
    private final BigDecimal stopLossPrice;
    private final List<BigDecimal> takeProfitPrices;
    private final BigDecimal expectedMonetaryRisk;
    private final BigDecimal riskRewardRatio;
    private final BigDecimal realizedPnl;
    private final BigDecimal actualFee;
    private final Instant closedAt;
    private final TradeOutcomeStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TradeOutcome(UUID id, UUID accountId, UUID brokerAccountId, UUID executionIntentId,
                         UUID tradePlanId, long tradePlanVersion, List<TradePlanProvenance> provenance,
                         String instrument, ExecutionParameters.Side side, BigDecimal quantity,
                         BigDecimal entryPrice, BigDecimal stopLossPrice, List<BigDecimal> takeProfitPrices,
                         BigDecimal expectedMonetaryRisk, BigDecimal riskRewardRatio,
                         BigDecimal realizedPnl, BigDecimal actualFee, Instant closedAt,
                         TradeOutcomeStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.brokerAccountId = Objects.requireNonNull(brokerAccountId);
        this.executionIntentId = Objects.requireNonNull(executionIntentId);
        this.tradePlanId = Objects.requireNonNull(tradePlanId);
        if (tradePlanVersion < 1) throw new IllegalArgumentException("trade plan version starts at 1");
        this.tradePlanVersion = tradePlanVersion;
        this.provenance = List.copyOf(Objects.requireNonNull(provenance));
        this.instrument = Objects.requireNonNull(instrument);
        this.side = Objects.requireNonNull(side);
        this.quantity = Objects.requireNonNull(quantity);
        this.entryPrice = entryPrice;
        this.stopLossPrice = stopLossPrice;
        this.takeProfitPrices = List.copyOf(takeProfitPrices == null ? List.of() : takeProfitPrices);
        this.expectedMonetaryRisk = expectedMonetaryRisk;
        this.riskRewardRatio = riskRewardRatio;
        this.realizedPnl = realizedPnl;
        this.actualFee = actualFee;
        this.closedAt = closedAt;
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static TradeOutcome acknowledged(UUID id, UUID accountId, UUID brokerAccountId,
                                            UUID executionIntentId, UUID tradePlanId,
                                            long tradePlanVersion, List<TradePlanProvenance> provenance,
                                            ExecutionParameters parameters, BigDecimal entryPrice,
                                            Instant now) {
        return new TradeOutcome(id, accountId, brokerAccountId, executionIntentId, tradePlanId,
                tradePlanVersion, provenance, parameters.instrument(), parameters.side(),
                parameters.quantity(), entryPrice, parameters.stopLossPrice(), parameters.takeProfitPrices(),
                parameters.expectedMonetaryRisk(), parameters.riskRewardRatio(), null, null, null,
                TradeOutcomeStatus.ENTRY_ACKNOWLEDGED, now, now);
    }

    public static TradeOutcome rehydrate(UUID id, UUID accountId, UUID brokerAccountId,
                                         UUID executionIntentId, UUID tradePlanId, long tradePlanVersion,
                                         List<TradePlanProvenance> provenance, String instrument,
                                         ExecutionParameters.Side side, BigDecimal quantity, BigDecimal entryPrice,
                                         BigDecimal stopLossPrice, List<BigDecimal> takeProfitPrices,
                                         BigDecimal expectedMonetaryRisk, BigDecimal riskRewardRatio,
                                         BigDecimal realizedPnl, BigDecimal actualFee, Instant closedAt,
                                         TradeOutcomeStatus status, Instant createdAt, Instant updatedAt) {
        return new TradeOutcome(id, accountId, brokerAccountId, executionIntentId, tradePlanId,
                tradePlanVersion, provenance, instrument, side, quantity, entryPrice, stopLossPrice,
                takeProfitPrices, expectedMonetaryRisk, riskRewardRatio, realizedPnl, actualFee, closedAt,
                status, createdAt, updatedAt);
    }

    public UUID id() { return id; }
    public UUID accountId() { return accountId; }
    public UUID brokerAccountId() { return brokerAccountId; }
    public UUID executionIntentId() { return executionIntentId; }
    public UUID tradePlanId() { return tradePlanId; }
    public long tradePlanVersion() { return tradePlanVersion; }
    public List<TradePlanProvenance> provenance() { return provenance; }
    public String instrument() { return instrument; }
    public ExecutionParameters.Side side() { return side; }
    public BigDecimal quantity() { return quantity; }
    public BigDecimal entryPrice() { return entryPrice; }
    public BigDecimal stopLossPrice() { return stopLossPrice; }
    public List<BigDecimal> takeProfitPrices() { return takeProfitPrices; }
    public BigDecimal expectedMonetaryRisk() { return expectedMonetaryRisk; }
    public BigDecimal riskRewardRatio() { return riskRewardRatio; }
    public BigDecimal realizedPnl() { return realizedPnl; }
    public BigDecimal actualFee() { return actualFee; }
    public Instant closedAt() { return closedAt; }
    public TradeOutcomeStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
