package com.hope.trading.market_data.dto;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
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
