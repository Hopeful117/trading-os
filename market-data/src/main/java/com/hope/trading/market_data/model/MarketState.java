package com.hope.trading.market_data.model;

import com.hope.trading.market_data.helper.MarketClosureReason;
import com.hope.trading.market_data.helper.TradingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.Instant;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketState {
    @Enumerated(EnumType.STRING)
    @Column(name = "trading_status")
    private TradingStatus tradingStatus;

    @Column(nullable = false)
    private boolean tradable;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason")
    private MarketClosureReason closureReason;

    private Instant lastUpdated;

}
