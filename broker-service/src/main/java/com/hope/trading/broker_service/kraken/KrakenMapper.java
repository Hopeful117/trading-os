package com.hope.trading.broker_service.kraken;

import com.hope.trading.broker_service.dto.*;
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
                .symbol(formatPair(symbol))
                .price(new BigDecimal(ticker.getC().getFirst()))
                .timestamp(Instant.now())
                .build();
    }
    public AccountBalance toAccountBalance(
            KrakenAccountBalanceResponse response
    ) {

        return AccountBalance.builder()
                .balances(response.getResult())
                .build();
    }

    public Position toPosition(String brokerPositionId, KrakenOpenPositionResult position) {

        return Position.builder()
                .brokerPositionId(brokerPositionId)
                .symbol(formatPair(position.getPair()))
                .side(position.getType())
                .quantity(position.getVol())
                .entryValue(position.getCost())
                .unrealizedPnl(position.getNet())
                .fee(position.getFee())
                .openedAt(
                        position.getTime() != null
                                ? Instant.ofEpochSecond(position.getTime().longValue())
                                : null
                )
                .build();
    }


    private String formatPair(String pair) {

        if (pair == null) {
            return null;
        }

        return pair
                .replace("XXBT", "BTC")
                .replace("XETH", "ETH")
                .replace("ZUSD", "USD")
                .replace("ZEUR", "EUR");
    }

}
