package com.hope.trading.market_data.model;



import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "markets",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "symbol", "product_type"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID marketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketProvider provider;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "provider_symbol")
    private String providerSymbol;

    @Column(nullable = false)
    private String baseAsset;

    @Column(nullable = false)
    private String quoteAsset;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private InstrumentProductType productType = InstrumentProductType.SPOT;

    @Column(name = "quantity_unit", length = 32)
    private String quantityUnit;

    @Column(name = "contract_multiplier", precision = 38, scale = 12)
    private BigDecimal contractMultiplier;

    @Column(name = "contract_source_id", length = 255)
    private String contractSourceId;

    @Column(name = "contract_source_version")
    private Long contractSourceVersion;

    @Column(name = "contract_observed_at")
    private Instant contractObservedAt;

    @Embedded
    @Column(nullable = false)
    private MarketConstraints marketConstraints;

    @Embedded
    @Column(nullable = false)
    private MarketState marketState;

}




