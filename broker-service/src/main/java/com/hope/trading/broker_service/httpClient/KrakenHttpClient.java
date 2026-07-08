package com.hope.trading.broker_service.httpClient;

import com.hope.trading.broker_service.dto.KrakenTickerResponse;

public interface KrakenHttpClient {
    KrakenTickerResponse getTicker(String symbol);


}
