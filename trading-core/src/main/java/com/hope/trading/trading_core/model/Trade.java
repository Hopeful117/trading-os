package com.hope.trading.trading_core.model;

import com.hope.trading.trading_core.helper.TradeType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Trade {
    @Id
    @GeneratedValue
    private UUID id;

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

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;
}
