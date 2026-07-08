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

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType type;

    @Column(nullable = false)
    private BigDecimal entryPrice;

    private BigDecimal exitPrice;

    @Column(nullable = false)
    private BigDecimal quantity;

    private BigDecimal pnl;

    private Instant openedAt;

    private Instant closedAt;

    private BigDecimal stopLoss;

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
