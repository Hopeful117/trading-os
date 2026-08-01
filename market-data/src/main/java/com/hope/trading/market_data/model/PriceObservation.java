package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "price_observations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PriceObservation {
    @Id
    private UUID observationId;
    @Column(nullable = false)
    private UUID marketId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketProvider provider;
    @Column(nullable = false)
    private String symbol;
    @Column(nullable = false)
    private String baseAsset;
    @Column(nullable = false)
    private String quoteAsset;
    @Column(precision = 38, scale = 18)
    private BigDecimal bid;
    @Column(precision = 38, scale = 18)
    private BigDecimal ask;
    @Column(name = "last_price", precision = 38, scale = 18)
    private BigDecimal last;
    @Column(nullable = false)
    private Instant effectiveAt;
    @Column(nullable = false)
    private Instant capturedAt;
}
