package com.hope.trading.trading_core.market_data.dto;

import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarketResponse {
    private UUID marketId;

    private MarketProvider provider;

    private String symbol;

    private String baseAsset;

    private String quoteAsset;

    private InstrumentProductType productType;

    private String quantityUnit;

    private BigDecimal contractMultiplier;

    private String contractSourceId;

    private Long contractSourceVersion;

    private Instant contractObservedAt;

    private MarketState marketState;

    private MarketConstraints marketConstraints;
}
