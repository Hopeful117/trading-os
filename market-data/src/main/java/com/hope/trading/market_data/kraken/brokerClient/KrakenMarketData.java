package com.hope.trading.market_data.kraken.brokerClient;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenRestOhlcEntry;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResponse;
import com.hope.trading.market_data.kraken.dto.ohlc.KrakenOhlcResult;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenAssetPairsResponse;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenRestTickerData;
import com.hope.trading.market_data.kraken.dto.ticker.KrakenTickerResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentDto;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentStatusDto;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerDto;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesInstrumentStatusResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesTickerResponse;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartCandle;
import com.hope.trading.market_data.kraken.dto.futures.KrakenFuturesChartResponse;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesOhlcMapper;
import com.hope.trading.market_data.kraken.helper.KrakenMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesMarketMapper;
import com.hope.trading.market_data.kraken.helper.KrakenProviderSymbolResolver;
import com.hope.trading.market_data.kraken.helper.KrakenFuturesTickerMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestTickerMapper;
import com.hope.trading.market_data.kraken.helper.KrakenRestOhlcMapper;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketActivityCalculationBasis;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.service.OhlcHistoryNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class KrakenMarketData implements MarketDataProvider {
    private static final int KRAKEN_MAX_OHLC_ENTRIES = 720;
    private static final Logger log = LoggerFactory.getLogger(KrakenMarketData.class);

    private final KrakenHttpClient client;
    private final KrakenFuturesHttpClient futuresClient;
    private final KrakenFuturesChartsHttpClient futuresChartsClient;
    private final KrakenMarketMapper marketMapper;
    private final KrakenFuturesMarketMapper futuresMarketMapper;
    private final KrakenProviderSymbolResolver symbolResolver;
    private final KrakenRestOhlcMapper restOhlcMapper;
    private final KrakenRestTickerMapper restTickerMapper;
    private final KrakenFuturesTickerMapper futuresTickerMapper;
    private final KrakenFuturesOhlcMapper futuresOhlcMapper;
    private final OhlcHistoryNormalizer historyNormalizer;
    private final Clock clock;

    public KrakenMarketData(
            KrakenHttpClient client, KrakenFuturesHttpClient futuresClient,
            KrakenMarketMapper marketMapper, KrakenFuturesMarketMapper futuresMarketMapper,
            KrakenProviderSymbolResolver symbolResolver, KrakenRestOhlcMapper restOhlcMapper,
            KrakenRestTickerMapper restTickerMapper, KrakenFuturesTickerMapper futuresTickerMapper,
            OhlcHistoryNormalizer historyNormalizer, Clock clock) {
        this(client, futuresClient, (tickType, symbol, resolution, count, from, to) -> null,
                marketMapper, futuresMarketMapper, symbolResolver, restOhlcMapper,
                restTickerMapper, futuresTickerMapper, new KrakenFuturesOhlcMapper(clock),
                historyNormalizer, clock);
    }

    @Autowired
    public KrakenMarketData(
            KrakenHttpClient client, KrakenFuturesHttpClient futuresClient,
            KrakenFuturesChartsHttpClient futuresChartsClient, KrakenMarketMapper marketMapper,
            KrakenFuturesMarketMapper futuresMarketMapper, KrakenProviderSymbolResolver symbolResolver,
            KrakenRestOhlcMapper restOhlcMapper, KrakenRestTickerMapper restTickerMapper,
            KrakenFuturesTickerMapper futuresTickerMapper, KrakenFuturesOhlcMapper futuresOhlcMapper,
            OhlcHistoryNormalizer historyNormalizer, Clock clock) {
        this.client = client;
        this.futuresClient = futuresClient;
        this.futuresChartsClient = futuresChartsClient;
        this.marketMapper = marketMapper;
        this.futuresMarketMapper = futuresMarketMapper;
        this.symbolResolver = symbolResolver;
        this.restOhlcMapper = restOhlcMapper;
        this.restTickerMapper = restTickerMapper;
        this.futuresTickerMapper = futuresTickerMapper;
        this.futuresOhlcMapper = futuresOhlcMapper;
        this.historyNormalizer = historyNormalizer;
        this.clock = clock;
    }

    @Override
    public List<Market> getMarkets() {
        KrakenAssetPairsResponse response = client.getAssetPairs();
        List<Market> spotMarkets = response == null || response.getResult() == null
                ? List.of()
                : response.getResult()
                .values()
                .stream()
                .map(marketMapper::toDomain)
                .toList();

        List<Market> futuresMarkets = discoverFuturesMarkets();

        return java.util.stream.Stream.concat(spotMarkets.stream(), futuresMarkets.stream()).toList();
    }

    private List<Market> discoverFuturesMarkets() {
        try {
            KrakenFuturesInstrumentResponse instruments = futuresClient.getInstruments();
            validateFuturesResponse(instruments, "instruments");
            KrakenFuturesInstrumentStatusResponse statuses = futuresClient.getInstrumentStatus();
            validateFuturesResponse(statuses, "instrument status");
            KrakenFuturesTickerResponse tickers = futuresClient.getTickers();
            validateFuturesResponse(tickers, "tickers");

            Set<String> currentlyTradable = statuses.getInstrumentStatus() == null
                ? Set.of()
                : statuses.getInstrumentStatus().stream()
                .map(KrakenFuturesInstrumentStatusDto::getTradeable)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .collect(Collectors.toUnmodifiableSet());
            Map<String, KrakenFuturesTickerDto> perpetualTickers = tickers.getTickers() == null
                ? Map.of()
                : tickers.getTickers().stream()
                .filter(ticker -> "perpetual".equalsIgnoreCase(ticker.getTag()))
                .filter(ticker -> ticker.getSymbol() != null && !ticker.getSymbol().isBlank())
                .collect(Collectors.toMap(
                        KrakenFuturesTickerDto::getSymbol,
                        Function.identity(),
                        (first, ignored) -> first
                ));

            return instruments.getInstruments() == null
                ? List.of()
                : instruments.getInstruments().stream()
                .filter(this::isSupportedPerpetual)
                .filter(instrument -> perpetualTickers.containsKey(instrument.getSymbol()))
                .map(instrument -> futuresMarketMapper.toDomain(
                        instrument,
                        currentlyTradable.contains(instrument.getSymbol())))
                .toList();
        } catch (RuntimeException exception) {
            log.warn("Unable to discover Kraken Futures markets; preserving Spot markets", exception);
            return List.of();
        }
    }

    @Override
    public MarketProvider getName() {
        return MarketProvider.KRAKEN;
    }

    @Override
    public Optional<TickerEvent> acquireCurrentSnapshot(Market market) {
        validateMarket(market);
        if (market.getProductType() == InstrumentProductType.LINEAR_PERPETUAL) {
            return acquireFuturesSnapshot(market);
        }

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

    private Optional<TickerEvent> acquireFuturesSnapshot(Market market) {
        String providerSymbol = symbolResolver.toRestPair(market);
        KrakenFuturesTickerResponse response;
        try {
            response = futuresClient.getTickers();
            validateFuturesResponse(response, "tickers");
        } catch (RuntimeException exception) {
            log.warn("Unable to acquire Kraken Futures ticker market={}", market.getSymbol(), exception);
            return Optional.empty();
        }
        if (response.getTickers() == null) {
            return Optional.empty();
        }

        KrakenFuturesTickerDto ticker = response.getTickers().stream()
                .filter(candidate -> providerSymbol.equals(candidate.getSymbol()))
                .filter(candidate -> "perpetual".equalsIgnoreCase(candidate.getTag()))
                .filter(candidate -> candidate.getBid() != null)
                .filter(candidate -> candidate.getAsk() != null)
                .filter(candidate -> candidate.getLast() != null)
                .filter(candidate -> candidate.getLastTime() != null)
                .findFirst()
                .orElse(null);
        if (ticker == null) {
            return Optional.empty();
        }
        return Optional.of(futuresTickerMapper.toEvent(ticker, market));
    }

    @Override
    public List<OhlcEvent> findOhlcHistory(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        return findOhlcHistorySnapshot(market, interval, limit).normalizedEvents();
    }

    @Override
    public MarketHistorySnapshot findOhlcHistorySnapshot(
            Market market,
            OhlcInterval interval,
            int limit
    ) {
        validateArguments(
                market,
                interval,
                limit
        );

        if (market.getProductType() == InstrumentProductType.LINEAR_PERPETUAL) {
            return findFuturesOhlcHistorySnapshot(market, interval, limit);
        }
        if (market.getProductType() != null
                && market.getProductType() != InstrumentProductType.SPOT) {
            throw new UnsupportedOperationException("Kraken OHLC history is unsupported for this product");
        }

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

        return new MarketHistorySnapshot(
                takeLastEvents(mappedEvents, limit),
                takeLastEvents(normalizedEvents, limit)
        );
    }

    private MarketHistorySnapshot findFuturesOhlcHistorySnapshot(
            Market market, OhlcInterval interval, int limit) {
        // Trade ticks are the deterministic Market Facts source; this is not a Spot fallback.
        KrakenFuturesChartResponse response = futuresChartsClient.getCandles(
                "trade", symbolResolver.toRestPair(market), futuresResolution(interval), limit, null, null);
        if (response == null || response.getCandles() == null) {
            throw new IllegalStateException("Kraken Futures Charts response is unavailable");
        }
        Instant fetchedAt = clock.instant();
        List<OhlcEvent> events = response.getCandles().stream()
                .map(candle -> futuresOhlcMapper.toEvent(candle, market, interval, fetchedAt))
                .sorted(java.util.Comparator.comparing(OhlcEvent::openTime))
                .toList();
        List<OhlcEvent> normalized = historyNormalizer.fillMissingIntervals(events, interval);
        return new MarketHistorySnapshot(
                takeLastEvents(events, limit), takeLastEvents(normalized, limit),
                activityCalculationBasis(events));
    }

    private MarketActivityCalculationBasis activityCalculationBasis(List<OhlcEvent> events) {
        return events.stream().allMatch(event -> event.vwap() == null)
                ? MarketActivityCalculationBasis.CLOSE_PRICE_FALLBACK
                : MarketActivityCalculationBasis.VWAP;
    }

    private String futuresResolution(OhlcInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> "1m";
            case FIVE_MINUTES -> "5m";
            case FIFTEEN_MINUTES -> "15m";
            case THIRTY_MINUTES -> "30m";
            case ONE_HOUR -> "1h";
            case FOUR_HOURS -> "4h";
            case TWELVE_HOURS -> "12h";
            case ONE_DAY -> "1d";
            case ONE_WEEK -> "1w";
        };
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
                occurredAt,
                clock.instant()
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

    private boolean isSupportedPerpetual(KrakenFuturesInstrumentDto instrument) {
        return instrument != null
                && "flexible_futures".equalsIgnoreCase(instrument.getType())
                && !Boolean.TRUE.equals(instrument.getIsExpired())
                && instrument.getSymbol() != null
                && !instrument.getSymbol().isBlank();
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

    private void validateFuturesResponse(Object response, String endpoint) {
        if (response == null) {
            throw new IllegalStateException("Kraken Futures " + endpoint + " response is empty");
        }
        String result;
        if (response instanceof KrakenFuturesInstrumentResponse instruments) {
            result = instruments.getResult();
        } else if (response instanceof KrakenFuturesInstrumentStatusResponse statuses) {
            result = statuses.getResult();
        } else if (response instanceof KrakenFuturesTickerResponse tickers) {
            result = tickers.getResult();
        } else {
            throw new IllegalArgumentException("Unsupported Kraken Futures response");
        }
        if (!"success".equalsIgnoreCase(result)) {
            throw new IllegalStateException("Kraken Futures " + endpoint + " returned " + result);
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
