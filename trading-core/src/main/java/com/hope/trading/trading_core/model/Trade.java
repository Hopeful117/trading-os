package com.hope.trading.trading_core.model;

import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
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
    @Column(name = "product_type", length = 32)
    private InstrumentProductType productType;

    @Column(name = "quote_currency", length = 16)
    private String quoteCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_unit", length = 32)
    private QuantityUnit quantityUnit;

    @Column(name = "contract_multiplier", precision = 38, scale = 12)
    private BigDecimal contractMultiplier;

    @Column(name = "base_currency", length = 16)
    private String baseCurrency;

    @Column(name = "price_precision")
    private Integer pricePrecision;

    @Column(name = "quantity_precision")
    private Integer quantityPrecision;

    @Column(name = "minimum_quantity", precision = 38, scale = 12)
    private BigDecimal minimumQuantity;

    @Column(name = "quantity_increment", precision = 38, scale = 12)
    private BigDecimal quantityIncrement;

    @Column(name = "contract_source_id", length = 255)
    private String contractSourceId;

    @Column(name = "contract_source_version")
    private Long contractSourceVersion;

    @Column(name = "contract_observed_at")
    private Instant contractObservedAt;

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

    /** Copies a validated provider-neutral contract snapshot onto the persisted position. */
    public void applyFinancialContract(InstrumentFinancialContract contract) {
        this.productType = contract.productType();
        this.baseCurrency = contract.baseCurrency().value();
        this.quoteCurrency = contract.quoteCurrency().value();
        this.quantityUnit = contract.quantityUnit();
        this.contractMultiplier = contract.contractMultiplier();
        this.pricePrecision = contract.pricePrecision();
        this.quantityPrecision = contract.quantityPrecision();
        this.minimumQuantity = contract.minimumQuantity();
        this.quantityIncrement = contract.quantityIncrement();
        this.contractSourceId = contract.provenance().sourceId();
        this.contractSourceVersion = contract.provenance().sourceVersion();
        this.contractObservedAt = contract.provenance().observedAt();
    }

}
