package com.hope.trading.broker_service.httpClient;

import com.hope.trading.broker_service.dto.KrakenTickerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KrakenRestClientImpl implements KrakenHttpClient {
    private final RestClient krakenRestClient;

    @Override
    public KrakenTickerResponse getTicker(String symbol) {
        return krakenRestClient.get().uri(uriBuilder -> uriBuilder.path("/0/public/Ticker"
                ).queryParam("pair",symbol).build()).retrieve().body(KrakenTickerResponse.class);
    }
}
