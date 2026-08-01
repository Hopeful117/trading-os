package com.hope.trading.market_data.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "valuation_conversion_legs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValuationConversionLeg {
    @Id
    private UUID legId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fact_id", nullable = false)
    private ValuationFact fact;
    @Column(nullable = false)
    private String fromCurrency;
    @Column(nullable = false)
    private String toCurrency;
    @Column(precision = 38, scale = 18)
    private BigDecimal rate;
    private UUID sourceObservationId;
    private UUID sourceMarketId;
    private String sourceProvider;
    private String sourceSymbol;
    @Column(nullable = false, length = 32)
    private String sourcePriceType;
    @Column(precision = 38, scale = 18)
    private BigDecimal sourcePrice;
    private Instant sourceEffectiveAt;
    private Instant sourceCapturedAt;
    @Column(length = 64)
    private String observationAge;

    public ValuationConversionLeg(String fromCurrency, String toCurrency, BigDecimal rate,
                                  PriceObservation source, String sourcePriceType, BigDecimal sourcePrice,
                                  String observationAge) {
        this.legId = UUID.randomUUID();
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.rate = rate;
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
    }

    void attachTo(ValuationFact fact) {
        this.fact = fact;
    }
}
