package com.hope.trading.trading_core.model;

import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Table(name = "trades")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID tradeId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType type;

    @Column(nullable = false, precision = 38, scale = 12)
    private BigDecimal entryPrice;

    @Column(precision = 38, scale = 12)
    private BigDecimal currentPrice;

    @Column(precision = 38, scale = 12)
    private BigDecimal exitPrice;

    @Column(name = "broker_position_reference", length = 255)
    private String brokerPositionReference;

    @Column(name = "broker_close_deal_reference", length = 255)
    private String brokerCloseDealReference;

    @Column(name = "broker_close_deal_references", length = 4000)
    private String brokerCloseDealReferences;

    @Column(name = "broker_baseline_close_deal_references", length = 4000)
    private String brokerBaselineCloseDealReferences;

    @Column(name = "broker_closed_quantity", precision = 38, scale = 12)
    private BigDecimal brokerClosedQuantity;

    @Column(name = "broker_original_quantity", precision = 38, scale = 12)
    private BigDecimal brokerOriginalQuantity;

    @Column(name = "broker_closed_notional", precision = 38, scale = 12)
    private BigDecimal brokerClosedNotional;

    @Column(nullable = false, precision = 38, scale = 12)
    private BigDecimal quantity;

    private BigDecimal pnl;

    @Column(name = "realized_pnl", precision = 38, scale = 12)
    private BigDecimal realizedPnl;

    private Instant openedAt;

    private Instant closedAt;

    @Column(precision = 38, scale = 12)
    private BigDecimal stopLoss;

    @Column(precision = 38, scale = 12)
    private BigDecimal takeProfit;

    private BigDecimal riskAmount;

    private BigDecimal rewardAmount;

    private BigDecimal riskRewardRatio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus tradeStatus;

}
