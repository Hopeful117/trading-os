package com.hope.trading.market_data.kraken.dto.ticker;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KrakenAssetPairsResponse {
    private List<String> error;
    private Map<String, KrakenAssetPairDto> result;
}
