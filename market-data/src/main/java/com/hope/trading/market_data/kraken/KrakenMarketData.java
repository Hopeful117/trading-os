package com.hope.trading.market_data.kraken;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KrakenMarketData  implements MarketDataProvider {
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

    @Override
    public MarketProvider getName(){
        return MarketProvider.KRAKEN;
    }
}
