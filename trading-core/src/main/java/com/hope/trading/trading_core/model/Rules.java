package com.hope.trading.trading_core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Rules {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID rulesId;

    @Column(nullable = false)
    private String name;

    private boolean active;


    @Column(nullable = false)
    private BigDecimal maxRiskPerTrade; // ex: 0.01 = 1%

    @Column(nullable = false)
    private BigDecimal maxDailyLoss; // ex: 0.05 = 5%

    @Column(nullable = false)
    private BigDecimal maxTotalDrawdown; // ex: 0.10 = 10%


    private Integer maxTradesPerDay;

    private Integer cooldownMinutesBetweenTrades;

    private BigDecimal maxLeverage;



    private String allowedSessions;



}
