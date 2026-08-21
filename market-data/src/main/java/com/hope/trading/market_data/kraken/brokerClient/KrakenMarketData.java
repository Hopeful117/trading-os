package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenRestTickerData;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerResponse;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenProviderSymbolResolver;
import com.hope.trading.market_data.kraken.helper.KrakenRestTickerMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class KrakenMarketData implements MarketDataProvider {
    private static final int KRAKEN_MAX_OHLC_ENTRIES = 720;

    private final KrakenHttpClient client;
    private final KrakenMarketMapper marketMapper;
    private final KrakenProviderSymbolResolver symbolResolver;
    private final KrakenRestOhlcMapper restOhlcMapper;
    private final KrakenRestTickerMapper restTickerMapper;
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
    public Optional<TickerEvent> acquireCurrentSnapshot(Market market) {
        validateMarket(market);
        String providerSymbol = symbolResolver.toRestPair(market);
        KrakenTickerResponse response = client.findTicker(providerSymbol);
        validateTickerResponse(response);

        Map<String, KrakenRestTickerData> result = response.getResult();
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        KrakenRestTickerData data = result.values().stream().findFirst().orElse(null);
        if (data == null) {
            return Optional.empty();
        }

        TickerEvent event = restTickerMapper.toEvent(data, market, Instant.now());
        if (event.bid() == null || event.ask() == null) {
            return Optional.empty();
        }
        return Optional.of(event);
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

        String providerSymbol = symbolResolver.toRestPair(market);

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

    private void validateArguments(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        validateMarket(market);

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

    private void validateMarket(Market market) {
        if (market == null) {
            throw new IllegalArgumentException("Market is required");
        }
        symbolResolver.toRestPair(market);
    }

    private void validateTickerResponse(KrakenTickerResponse response) {
        if (response == null) {
            throw new IllegalStateException("Kraken ticker response is required");
        }
        if (response.getError() != null && !response.getError().isEmpty()) {
            throw new IllegalStateException(
                    "Kraken ticker request failed: " + String.join(", ", response.getError())
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
