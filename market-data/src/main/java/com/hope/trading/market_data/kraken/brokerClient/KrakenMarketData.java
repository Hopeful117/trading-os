package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class KrakenMarketData implements MarketDataProvider {
    private static final int KRAKEN_MAX_OHLC_ENTRIES = 720;

    private final KrakenHttpClient client;
    private final KrakenMarketMapper marketMapper;
    private final KrakenRestOhlcMapper restOhlcMapper;
    private final OhlcHistoryNormalizer historyNormalizer;

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
    public MarketProvider getName() {
        return MarketProvider.KRAKEN;
    }

    @Override
    public List<OhlcEvent> findOhlcHistory(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        validateArguments(
                market,
                interval,
                limit
        );

        String providerSymbol = resolveProviderSymbol(market);

        KrakenOhlcResponse response = client.findOhlcHistory(
                providerSymbol,
                interval.getMinutes()
        );

        KrakenOhlcResult result = restOhlcMapper.extract(response);

        List<OhlcEvent> mappedEvents = mapEntries(
                result.entries(),
                market,
                interval,
                result.last()
        );

        List<OhlcEvent> normalizedEvents =
                historyNormalizer.fillMissingIntervals(mappedEvents, interval);

        return takeLastEvents(normalizedEvents, limit);
    }

    private List<OhlcEvent> mapEntries(
            List<KrakenRestOhlcEntry> entries,
            Market market,
            OhlcInterval interval,
            Instant occurredAt
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return IntStream.range(0, entries.size())
                .mapToObj(index -> {
                    KrakenRestOhlcEntry entry = entries.get(index);
                    boolean closed = index < entries.size() - 1;

                    return restOhlcMapper.toEvent(
                            entry,
                            market,
                            interval,
                            closed,
                            occurredAt
                    );
                })
                .toList();
    }

    private String resolveProviderSymbol(Market market) {
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

    private List<OhlcEvent> takeLastEvents(
            List<OhlcEvent> events,
            int limit
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, events.size() - limit);

        return List.copyOf(
                events.subList(fromIndex, events.size())
        );
    }
}
