package com.hope.trading.trading_core.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private BigDecimal equity;

    @Column(nullable = false)
    private BigDecimal maxDrawdown;

    @Column(nullable = false)
    private BigDecimal dailyLossLimit;

    private boolean active;
}

