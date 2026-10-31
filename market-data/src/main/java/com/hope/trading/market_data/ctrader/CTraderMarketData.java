package com.hope.trading.market_data.ctrader;

import com.hope.trading.ctrader.openapi.codec.CTraderPriceDecoder;
import com.hope.trading.market_data.brokerclient.MarketDataProvider;
import com.hope.trading.market_data.ctrader.authentication.CTraderMarketDataProperties;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.MarketStateBuilder;
import com.hope.trading.market_data.helper.MarketAvailability;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketActivityCalculationBasis;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.model.TickerEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderMarketData implements MarketDataProvider {
    // Calendar lookback must account for provider-closed periods while the row count remains bounded.
    private static final int INITIAL_LOOKBACK_MARGIN = 3;
    private static final int MAX_LOOKBACK_ATTEMPTS = 4;

    private final CTraderOpenApiClient client;
    private final CTraderMarketDataProperties properties;
    private final MarketStateBuilder marketStateBuilder;
    private final Clock clock;

    public CTraderMarketData(CTraderOpenApiClient client, CTraderMarketDataProperties properties,
                             MarketStateBuilder marketStateBuilder, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.marketStateBuilder = marketStateBuilder;
        this.clock = clock;
    }

    @Override
    public List<Market> getMarkets() {
        List<Market> markets = client.symbols().stream().map(this::toMarket)
                .filter(Optional::isPresent).map(Optional::get).toList();
        Map<String, String> providerSymbolsByIdentity = new HashMap<>();
        for (Market market : markets) {
            String identity = market.getSymbol() + ":" + market.getProductType();
            String previous = providerSymbolsByIdentity.putIfAbsent(identity, market.getProviderSymbol());
            if (previous != null && !previous.equals(market.getProviderSymbol())) {
                throw new IllegalStateException("Ambiguous cTrader mapping for " + identity);
            }
        }
        return markets;
    }

    @Override
    public MarketProvider getName() { return MarketProvider.CTRADER; }

    @Override
    public Optional<TickerEvent> acquireCurrentSnapshot(Market market) {
        return Optional.empty();
    }

    @Override
    public List<OhlcEvent> findOhlcHistory(Market market, OhlcInterval interval, int limit) {
        return findOhlcHistorySnapshot(market, interval, limit).normalizedEvents();
    }

    @Override
    public MarketHistorySnapshot findOhlcHistorySnapshot(Market market, OhlcInterval interval, int limit) {
        validate(market, interval, limit);
        long symbolId = Long.parseLong(market.getProviderSymbol());
        Instant to = clock.instant();
        CTraderOpenApiClient.CTraderSymbol symbol = new CTraderOpenApiClient.CTraderSymbol(
                symbolId,
                market.getSymbol(),
                market.getMarketConstraints().pricePrecision(),
                0,
                0,
                0
        );
        int lookbackSlots = Math.min(properties.maxTrendbarRows(), limit + INITIAL_LOOKBACK_MARGIN);
        List<OhlcEvent> events = List.of();
        for (int attempt = 0; attempt < MAX_LOOKBACK_ATTEMPTS; attempt++) {
            Instant from = to.minus(interval.getDuration().multipliedBy(lookbackSlots));
            events = client.trendbars(symbolId, period(interval), from, to, limit).stream()
                    .map(bar -> toEvent(market, interval, symbol, bar, to))
                    .sorted(Comparator.comparing(OhlcEvent::openTime))
                    .toList();
            if (events.stream().filter(OhlcEvent::closed).count() >= limit
                    || lookbackSlots >= properties.maxTrendbarRows()) {
                break;
            }
            lookbackSlots = Math.min(properties.maxTrendbarRows(), lookbackSlots * 2);
        }
        return new MarketHistorySnapshot(events, events.stream().filter(OhlcEvent::closed).toList(),
                MarketActivityCalculationBasis.CLOSE_PRICE_FALLBACK);
    }

    private Optional<Market> toMarket(CTraderOpenApiClient.CTraderSymbol symbol) {
        String canonical = properties.symbolMappings().get(symbol.name());
        if (canonical == null || canonical.isBlank()) return Optional.empty();
        String[] assets = canonical.split("/", 2);
        if (assets.length != 2 || assets[0].isBlank() || assets[1].isBlank()) return Optional.empty();
        return productType(symbol.name()).map(type -> Market.builder()
                .provider(MarketProvider.CTRADER).symbol(canonical)
                .providerSymbol(Long.toString(symbol.id())).baseAsset(assets[0]).quoteAsset(assets[1])
                .productType(type)
                .quantityUnit(hasCompleteContract(symbol.name(), type) ? "UNDERLYING_UNITS" : null)
                .contractMultiplier(persistedContractMultiplier(symbol.name(), type))
                .contractSourceId(contractSourceId(canonical, symbol.name(), type))
                .contractSourceVersion(contractSourceVersion(symbol.name(), type))
                .contractObservedAt(contractObservedAt(symbol.name(), type))
                // Symbol metadata alone is not proof that an order is executable now.
                .marketState(marketStateBuilder.buildInitialState(MarketAvailability.UNAVAILABLE))
                .marketConstraints(MarketConstraints.builder()
                        // cTrader volumes are expressed in centi-units.
                        .minimumOrderSize(canonicalQuantity(symbol.minVolume()))
                        .maximumOrderSize(canonicalQuantity(symbol.maxVolume()))
                        .orderStepSize(canonicalQuantity(symbol.stepVolume()))
                        .quantityPrecision(quantityPrecision(
                                canonicalQuantity(symbol.minVolume()),
                                canonicalQuantity(symbol.maxVolume()),
                                canonicalQuantity(symbol.stepVolume())))
                        .tickSize(BigDecimal.ONE.movePointLeft(symbol.digits()))
                .pricePrecision(symbol.digits()).build()).build());
    }

    private BigDecimal contractMultiplier(String providerSymbol, InstrumentProductType productType) {
        if (productType != InstrumentProductType.CFD) return null;
        String configured = properties.symbolContractMultipliers().get(providerSymbol);
        if (configured == null || configured.isBlank()) return null;
        BigDecimal multiplier = new BigDecimal(configured.trim());
        if (multiplier.signum() <= 0) throw new IllegalStateException("Invalid CFD contract multiplier for " + providerSymbol);
        return multiplier;
    }

    private boolean hasCompleteContract(String providerSymbol, InstrumentProductType productType) {
        if (productType != InstrumentProductType.CFD) return false;
        return contractMultiplier(providerSymbol, productType) != null
                && properties.symbolContractSourceVersions().get(providerSymbol) != null
                && !properties.symbolContractSourceVersions().get(providerSymbol).isBlank();
    }

    private BigDecimal persistedContractMultiplier(String providerSymbol, InstrumentProductType productType) {
        return hasCompleteContract(providerSymbol, productType)
                ? contractMultiplier(providerSymbol, productType) : null;
    }

    private String contractSourceId(String canonicalSymbol, String providerSymbol,
                                    InstrumentProductType productType) {
        return hasCompleteContract(providerSymbol, productType)
                ? "market-data-contract:" + canonicalSymbol : null;
    }

    private Long contractSourceVersion(String providerSymbol, InstrumentProductType productType) {
        if (!hasCompleteContract(providerSymbol, productType)) return null;
        String configured = properties.symbolContractSourceVersions().get(providerSymbol);
        if (configured == null || configured.isBlank()) return null;
        long version = Long.parseLong(configured.trim());
        if (version < 1) throw new IllegalStateException("Invalid CFD contract source version for " + providerSymbol);
        return version;
    }

    private Instant contractObservedAt(String providerSymbol, InstrumentProductType productType) {
        return hasCompleteContract(providerSymbol, productType) ? clock.instant() : null;
    }

    private BigDecimal canonicalQuantity(long providerVolume) {
        if (providerVolume <= 0) throw new IllegalStateException("Invalid cTrader volume constraint");
        return BigDecimal.valueOf(providerVolume).movePointLeft(2);
    }

    private int quantityPrecision(BigDecimal... canonicalQuantities) {
        int precision = 0;
        for (BigDecimal quantity : canonicalQuantities) {
            precision = Math.max(precision, quantity.stripTrailingZeros().scale());
        }
        return precision;
    }

    private Optional<InstrumentProductType> productType(String providerSymbol) {
        String configured = properties.symbolProductTypes().get(providerSymbol);
        if (configured == null) {
            log.warn("CTRADER_MARKET_REJECTED_UNCLASSIFIED providerSymbol={} reason=missing_product_type_mapping",
                    providerSymbol);
            return Optional.empty();
        }
        if (configured.isBlank()) {
            log.warn("CTRADER_MARKET_REJECTED_UNCLASSIFIED providerSymbol={} reason=blank_product_type_mapping",
                    providerSymbol);
            return Optional.empty();
        }
        try {
            return Optional.of(InstrumentProductType.valueOf(configured.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid cTrader product type for provider symbol "
                    + providerSymbol, exception);
        }
    }

    private OhlcEvent toEvent(Market market, OhlcInterval interval, CTraderOpenApiClient.CTraderSymbol symbol,
                              CTraderOpenApiClient.CTraderTrendbar bar, Instant cutoff) {
        Instant open = Instant.ofEpochSecond(bar.timestamp());
        Instant close = open.plus(interval.getDuration());
        BigDecimal low = CTraderPriceDecoder.decode(bar.low(), symbol.digits());
        return new OhlcEvent(market.getMarketId(), MarketProvider.CTRADER, market.getSymbol(), interval, open, close,
                low.add(CTraderPriceDecoder.decode(bar.open(), symbol.digits())),
                low.add(CTraderPriceDecoder.decode(bar.high(), symbol.digits())), low,
                low.add(CTraderPriceDecoder.decode(bar.close(), symbol.digits())),
                bar.volume() > 0 ? BigDecimal.valueOf(bar.volume()) : null, null, null,
                !close.isAfter(cutoff), open, false,
                new CTraderInstrumentIdentity(symbol.id(), symbol.name(), properties.environment().name()).provenance(),
                clock.instant(), Long.toString(symbol.id()));
    }

    private String period(OhlcInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> "M1";
            case FIVE_MINUTES -> "M5";
            case FIFTEEN_MINUTES -> "M15";
            case THIRTY_MINUTES -> "M30";
            case ONE_HOUR -> "H1";
            case FOUR_HOURS -> "H4";
            case TWELVE_HOURS -> "H12";
            case ONE_DAY -> "D1";
            case ONE_WEEK -> "W1";
        };
    }

    private void validate(Market market, OhlcInterval interval, int limit) {
        if (market == null || market.getProvider() != MarketProvider.CTRADER) throw new IllegalArgumentException("cTrader market is required");
        if (interval == null || limit < 1 || limit > 720) throw new IllegalArgumentException("bounded cTrader OHLC request is required");
        if (market.getProviderSymbol() == null || !market.getProviderSymbol().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("cTrader provider symbol must be a numeric symbol id");
        }
    }
}
