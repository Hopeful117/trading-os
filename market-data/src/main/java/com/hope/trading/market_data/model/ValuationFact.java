package com.hope.trading.market_data.model;

import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest.PriceUse;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse.FactStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "valuation_facts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValuationFact {
    @Id
    private UUID factId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_version", nullable = false)
    private ValuationSnapshot snapshot;
    @Column(nullable = false, length = 32)
    private String requestType;
    @Column(nullable = false)
    private String requestId;
    private UUID marketId;
    private String asset;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PriceUse priceUse;
    private UUID sourceObservationId;
    private UUID sourceMarketId;
    private String sourceProvider;
    private String sourceSymbol;
    @Column(length = 32)
    private String sourcePriceType;
    @Column(precision = 38, scale = 18)
    private BigDecimal sourcePrice;
    private Instant sourceEffectiveAt;
    private Instant sourceCapturedAt;
    @Column(length = 64)
    private String observationAge;
    @Column(name = "valuation_value", precision = 38, scale = 18)
    private BigDecimal value;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private FactStatus status;
    @OneToMany(mappedBy = "fact", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ValuationConversionLeg> conversionLegs = new ArrayList<>();

    public ValuationFact(String requestType, String requestId, UUID marketId, String asset, PriceUse priceUse,
                         PriceObservation source, String sourcePriceType, BigDecimal sourcePrice,
                         String observationAge,
                         BigDecimal value, FactStatus status) {
        this.factId = UUID.randomUUID();
        this.requestType = requestType;
        this.requestId = requestId;
        this.marketId = marketId;
        this.asset = asset;
        this.priceUse = priceUse;
        if (source != null) {
            this.sourceObservationId = source.getObservationId();
            this.sourceMarketId = source.getMarketId();
            this.sourceProvider = source.getProvider().name();
            this.sourceSymbol = source.getSymbol();
            this.sourceEffectiveAt = source.getEffectiveAt();
            this.sourceCapturedAt = source.getCapturedAt();
        }
        this.sourcePriceType = sourcePriceType;
        this.sourcePrice = sourcePrice;
        this.observationAge = observationAge;
        this.value = value;
        this.status = status;
    }

    void attachTo(ValuationSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void addConversionLeg(ValuationConversionLeg leg) {
        conversionLegs.add(leg);
        leg.attachTo(this);
    }
}
