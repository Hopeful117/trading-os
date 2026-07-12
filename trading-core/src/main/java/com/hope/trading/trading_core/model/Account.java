package com.hope.trading.trading_core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "accounts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID accountId;

    @Column(unique = true)
    private String broker;


    @Column(nullable = false)
    private String name;


    @Column(nullable = false)
    private String baseCurrency;


    @Builder.Default
    @OneToMany(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AccountBalance> balances = new ArrayList<>();


    @Builder.Default
    @Column(nullable = false)
    private BigDecimal peakEquity = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private BigDecimal equity = BigDecimal.ZERO;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id")
    private Rules rules;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;


    @Builder.Default
    @OneToMany(
            fetch = FetchType.LAZY,
            mappedBy = "account"
    )
    private List<Trade> trades = new ArrayList<>();


    public void addBalance(AccountBalance balance) {
        balances.add(balance);
        balance.setAccount(this);
    }

    public void addTrade(Trade trade){
        trades.add(trade);
        trade.setAccount(this);
    }


}

