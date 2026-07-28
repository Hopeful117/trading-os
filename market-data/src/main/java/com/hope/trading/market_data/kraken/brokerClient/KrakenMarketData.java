package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class KrakenMarketData  implements MarketDataProvider {
    private final KrakenHttpClient client;
    private final KrakenMarketMapper marketMapper;
    private static final int KRAKEN_MAX_OHLC_ENTRIES = 720;
    private final KrakenRestOhlcMapper restOhlcMapper;


    @Override
    public List<Market> getMarkets() {

        KrakenAssetPairsResponse response = client.getAssetPairs();

        return response.getResult()
                .values()
                .stream()
                .map(marketMapper::toDomain)
                .toList();
    }

    @Override
    public MarketProvider getName(){
        return MarketProvider.KRAKEN;
    }

    @Override
    public List<OhlcEvent> findOhlcHistory(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        validateArguments(market, interval, limit);

        String providerSymbol =
                resolveProviderSymbol(market);

        log.info(
                "Requesting Kraken OHLC history symbol={} interval={} limit={}",
                providerSymbol,
                interval,
                limit
        );

        KrakenOhlcResponse response =
                client.findOhlcHistory(
                        providerSymbol,
                        interval.getMinutes()
                );

        KrakenOhlcResult result =
                restOhlcMapper.extract(response);

        List<KrakenRestOhlcEntry> selectedEntries =
                takeLast(
                        result.entries(),
                        limit
                );

        List<OhlcEvent> events =
                mapEntries(
                        selectedEntries,
                        market,
                        interval
                );

        log.info(
                "Received Kraken OHLC history symbol={} interval={} entries={}",
                providerSymbol,
                interval,
                events.size()
        );

        return events;
    }

    private List<OhlcEvent> mapEntries(
            List<KrakenRestOhlcEntry> entries,
            Market market,
            OhlcInterval interval
    ) {


        return IntStream.range(0, entries.size())
                .mapToObj(index -> {
                    KrakenRestOhlcEntry entry =
                            entries.get(index);

                    boolean closed =
                            index < entries.size() - 1;

                    return restOhlcMapper.toEvent(
                            entry,
                            market,
                            interval,
                            closed,
                            entries.getLast().openTime()

                    );
                })
                .toList();
    }

    private List<KrakenRestOhlcEntry> takeLast(
            List<KrakenRestOhlcEntry> entries,
            int limit
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        int fromIndex =
                Math.max(
                        0,
                        entries.size() - limit
                );

        return List.copyOf(
                entries.subList(
                        fromIndex,
                        entries.size()
                )
        );
    }

    private String resolveProviderSymbol(
            Market market
    ) {
        String symbol = market.getSymbol();

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Market provider symbol is required"
            );
        }

        return symbol;
    }

    private void validateArguments(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        if (market == null) {
            throw new IllegalArgumentException(
                    "Market is required"
            );
        }

        if (interval == null) {
            throw new IllegalArgumentException(
                    "OHLC interval is required"
            );
        }

        if (limit < 1
                || limit > KRAKEN_MAX_OHLC_ENTRIES) {
            throw new IllegalArgumentException(
                    "OHLC history limit must be between 1 and "
                            + KRAKEN_MAX_OHLC_ENTRIES
            );
        }
    }
}
