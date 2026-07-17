package com.hope.trading.market_data.brokerClient;

import com.hope.trading.market_data.dto.KrakenAssetPairsResponse;
import com.hope.trading.market_data.helper.KrakenMarketMapper;
import com.hope.trading.market_data.httpClient.KrakenHttpClient;
import com.hope.trading.market_data.model.Market;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KrakenMarketData  implements MarketDataProvider{
    private final KrakenHttpClient client;
    private final KrakenMarketMapper mapper;


    @Override
    public List<Market> getMarkets() {

        KrakenAssetPairsResponse response = client.getAssetPairs();

        return response.getResult()
                .values()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
