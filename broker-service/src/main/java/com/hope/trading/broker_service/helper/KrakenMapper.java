package com.hope.trading.broker_service.helper;

import com.hope.trading.broker_service.dto.KrakenTickerResponse;
import com.hope.trading.broker_service.dto.KrakenTickerResult;
import com.hope.trading.broker_service.dto.MarketPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class KrakenMapper {
    public MarketPrice toMarketPrice(
            KrakenTickerResponse response,
            String symbol
    ) {

        KrakenTickerResult ticker =
                response.getResult().values()
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "No ticker data returned by Kraken"
                                )
                        );


        return MarketPrice.builder()
                .symbol(symbol)
                .price(new BigDecimal(ticker.getC().getFirst()))
                .timestamp(Instant.now())
                .build();
    }
}
