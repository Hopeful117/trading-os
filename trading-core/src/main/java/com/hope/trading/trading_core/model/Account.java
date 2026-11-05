package com.hope.trading.trading_core.model;

import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
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

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "broker_account_id", unique = true)
    private UUID brokerAccountId;

    @Column
    private String broker;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_model", nullable = false, length = 32)
    private SettlementModel settlementModel = SettlementModel.INVENTORY;

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

    @Column(name = "realized_balance")
    private BigDecimal realizedBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_status", length = 32)
    private AccountValuationStatus valuationStatus;

    @Column(name = "valued_at")
    private java.time.Instant valuedAt;

    @Column(name = "market_data_timestamp")
    private java.time.Instant marketDataTimestamp;

    @Column(name = "broker_snapshot_completeness", length = 32)
    private String brokerSnapshotCompleteness;

    @Column(name = "broker_snapshot_freshness", length = 32)
    private String brokerSnapshotFreshness;

    @Column(name = "broker_snapshot_position_availability", length = 32)
    private String brokerSnapshotPositionAvailability;

    @Column(name = "broker_snapshot_order_availability", length = 32)
    private String brokerSnapshotOrderAvailability;

    @Column(name = "broker_snapshot_pnl_availability", length = 32)
    private String brokerSnapshotPnlAvailability;

    @Column(name = "broker_snapshot_deal_availability", length = 32)
    private String brokerSnapshotDealAvailability;

    @Column(name = "broker_snapshot_observed_at")
    private java.time.Instant brokerSnapshotObservedAt;

    @Column(name = "broker_snapshot_completed_at")
    private java.time.Instant brokerSnapshotCompletedAt;

    @Column(name = "broker_snapshot_unavailability_reasons", length = 4000)
    private String brokerSnapshotUnavailabilityReasons;

    @Column(name = "broker_derived_equity_candidate", precision = 30, scale = 10)
    private BigDecimal brokerDerivedEquityCandidate;

    @Column(name = "broker_derived_equity_currency", length = 16)
    private String brokerDerivedEquityCurrency;

    @Column(name = "broker_derived_equity_provenance", length = 32)
    private String brokerDerivedEquityProvenance;

    @Column(name = "broker_derived_equity_calculation_version", length = 64)
    private String brokerDerivedEquityCalculationVersion;

    @Column(name = "broker_derived_equity_hypothesis", length = 128)
    private String brokerDerivedEquityHypothesis;

    @Column(name = "broker_derived_equity_consistency_status", length = 32)
    private String brokerDerivedEquityConsistencyStatus;

    @Column(name = "broker_derived_equity_validation_status", length = 32)
    private String brokerDerivedEquityValidationStatus;

    @Column(name = "broker_derived_equity_observed_at")
    private java.time.Instant brokerDerivedEquityObservedAt;

    @Column(name = "broker_derived_equity_captured_at")
    private java.time.Instant brokerDerivedEquityCapturedAt;

    @Column(name = "broker_derived_equity_reasons", length = 4000)
    private String brokerDerivedEquityReasons;

    @Column(name = "starting_balance")
    private BigDecimal startingBalance;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id")
    private Rules rules;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;


    @Builder.Default
    @OneToMany(
            fetch = FetchType.LAZY,
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true
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
