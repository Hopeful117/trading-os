package com.hope.trading.market_data.kraken.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KrakenAssetPairDto {

    private String altname;

    private String wsname;

    private String base;

    private String quote;


    @JsonProperty("lot_decimals")
    private Integer quantityPrecision;


    @JsonProperty("pair_decimals")
    private Integer pricePrecision;


    @JsonProperty("ordermin")
    private BigDecimal minimumOrderSize;


    @JsonProperty("costmin")
    private BigDecimal minimumCost;


    @JsonProperty("tick_size")
    private BigDecimal tickSize;


    private String status;
}

