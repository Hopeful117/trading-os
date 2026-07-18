package com.hope.trading.market_data.model;



import com.hope.trading.market_data.helper.MarketProvider;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "markets",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "symbol"})
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

    @Column(nullable = false)
    private String baseAsset;

    @Column(nullable = false)
    private String quoteAsset;

    @Embedded
    @Column(nullable = false)
    private MarketConstraints marketConstraints;

    @Embedded
    @Column(nullable = false)
    private MarketState marketState;

}







