package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trade_outcome", uniqueConstraints = @UniqueConstraint(
        name = "uk_trade_outcome_execution_intent", columnNames = "execution_intent_id"))
public class TradeOutcomeEntity {
    @Id public UUID id;
    @Column(name = "account_id", nullable = false, updatable = false) public UUID accountId;
    @Column(name = "broker_account_id", nullable = false, updatable = false) public UUID brokerAccountId;
    @Column(name = "execution_intent_id", nullable = false, updatable = false) public UUID executionIntentId;
    @Column(name = "trade_plan_id", nullable = false, updatable = false) public UUID tradePlanId;
    @Column(name = "trade_plan_version", nullable = false, updatable = false) public long tradePlanVersion;
    @Column(nullable = false, updatable = false) public String instrument;
    @Column(nullable = false, updatable = false) public String side;
    @Column(nullable = false, precision = 30, scale = 12, updatable = false) public BigDecimal quantity;
    @Column(name = "entry_price", precision = 30, scale = 12, updatable = false) public BigDecimal entryPrice;
    @Column(name = "stop_loss_price", precision = 30, scale = 12, updatable = false) public BigDecimal stopLossPrice;
    @Column(name = "take_profit_prices", columnDefinition = "TEXT", updatable = false) public String takeProfitPrices;
    @Column(name = "expected_monetary_risk", precision = 30, scale = 12, updatable = false) public BigDecimal expectedMonetaryRisk;
    @Column(name = "risk_reward_ratio", precision = 30, scale = 12, updatable = false) public BigDecimal riskRewardRatio;
    @Column(name = "realized_pnl", precision = 30, scale = 12) public BigDecimal realizedPnl;
    @Column(name = "actual_fee", precision = 30, scale = 12) public BigDecimal actualFee;
    @Column(name = "closed_at") public Instant closedAt;
    @Column(nullable = false) public String status;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Version public long version;
    @ElementCollection
    @CollectionTable(name = "trade_outcome_provenance", joinColumns = @JoinColumn(name = "trade_outcome_id"))
    public List<TradeOutcomeProvenanceEntity> provenance = new ArrayList<>();
}
