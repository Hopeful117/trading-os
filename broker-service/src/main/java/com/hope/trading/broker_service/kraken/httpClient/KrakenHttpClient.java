package com.hope.trading.broker_service.kraken.httpClient;

import com.hope.trading.broker_service.dto.KrakenAccountBalanceResponse;
import com.hope.trading.broker_service.dto.KrakenOpenPositionResponse;
import com.hope.trading.broker_service.dto.KrakenTickerResponse;

public interface KrakenHttpClient {
    KrakenTickerResponse getTicker(String symbol);
    KrakenAccountBalanceResponse getBalances();
    KrakenOpenPositionResponse getOpenPositions();


}
