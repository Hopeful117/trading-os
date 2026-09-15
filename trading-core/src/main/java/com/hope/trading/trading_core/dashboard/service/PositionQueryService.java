package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.integration.PositionFact;
import com.hope.trading.trading_core.dashboard.integration.MarketDataDashboardMapper;
import com.hope.trading.trading_core.dashboard.integration.MarketPriceFact;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.model.PositionSource;
import com.hope.trading.trading_core.dashboard.model.PositionProtectionStatus;
import com.hope.trading.trading_core.dashboard.model.PositionValuationStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionQueryService {
    private final MarketDataClient marketDataClient;
    private final MarketDataDashboardMapper marketMapper;
    private final PositionValuationService valuationService;

    public List<OpenPositionDashboardView> findPositions(
            UUID accountId,
            List<PositionFact> brokerPositions,
            BigDecimal equity,
            Instant calculatedAt
    ) {
        return findPositions(accountId, brokerPositions, equity, calculatedAt, PositionSource.BROKER, null);
    }

    private List<OpenPositionDashboardView> findPositions(
            UUID accountId,
            List<PositionFact> brokerPositions,
            BigDecimal equity,
            Instant calculatedAt,
            PositionSource source,
            String accountCurrency
    ) {
        if (brokerPositions.isEmpty()) {
            return List.of();
        }

        List<String> warnings = new ArrayList<>();
        MarketLookup marketLookup = loadMarkets(brokerPositions, warnings);
        Map<UUID, MarketPriceFact> prices = loadPrices(marketLookup.marketIds(), warnings);
        Map<UUID, MarketPriceFact> safePrices = prices == null ? Map.of() : prices;

        return buildPositions(accountId, brokerPositions, marketLookup.bySymbol(), safePrices, equity,
                calculatedAt, source, accountCurrency, marketLookup.available());
    }

    private List<OpenPositionDashboardView> buildPositions(
            UUID accountId,
            List<PositionFact> brokerPositions,
            Map<String, MarketResponse> markets,
            Map<UUID, MarketPriceFact> prices,
            BigDecimal equity,
            Instant calculatedAt,
            PositionSource source,
            String accountCurrency,
            boolean marketCatalogAvailable
    ) {
        return brokerPositions.stream().map(position -> {
            MarketResponse market = markets.get(position.symbol());
            MarketPriceFact price = market == null ? null : prices.get(market.getMarketId());
            BigDecimal currentPrice = currentPrice(position, market, price, accountCurrency);
            PositionValuation value = valuationService.value(position, currentPrice, equity);
            return new OpenPositionDashboardView(
                    position.positionId(), accountId,
                    market == null ? null : market.getMarketId(),
                    position.symbol(), position.side(), position.quantity(),
                    position.entryPrice(), currentPrice, position.stopLoss(), position.takeProfit(),
                    value.pnl(), value.pnlPercentage(), position.brokerUnrealizedPnl(),
                    value.riskAmount(), value.riskPercentage(), currentPrice == null ? null : value.exposure(),
                    position.stopLoss() == null
                            ? PositionProtectionStatus.MISSING_STOP_LOSS
                            : PositionProtectionStatus.PROTECTED,
                    price != null && price.tradable(),
                    position.openedAt(), price == null ? null : price.occurredAt(), calculatedAt,
                    source, valuationStatus(market, price, currentPrice, accountCurrency, marketCatalogAvailable)
            );
        }).toList();
    }

    public List<OpenPositionDashboardView> findPaperPositions(Account account, Instant calculatedAt) {
        List<PositionFact> positions = account.getTrades().stream()
                .filter(trade -> trade.getTradeStatus() == TradeStatus.OPEN)
                .map(this::toPositionFact)
                .toList();
        return findPositions(account.getAccountId(), positions, account.getEquity(), calculatedAt,
                PositionSource.TRADING_CORE, account.getBaseCurrency());
    }

    private PositionFact toPositionFact(Trade trade) {
        if (trade.getTradeId() == null || trade.getType() == null || trade.getQuantity() == null
                || trade.getQuantity().signum() <= 0 || trade.getEntryPrice() == null
                || trade.getEntryPrice().signum() <= 0 || trade.getSymbol() == null
                || trade.getSymbol().isBlank()) {
            throw new IllegalStateException("Invalid PAPER position state");
        }
        return new PositionFact(
                trade.getTradeId().toString(), trade.getSymbol(), trade.getType(), trade.getQuantity(),
                trade.getEntryPrice(), trade.getStopLoss(), trade.getTakeProfit(), null, null, null,
                trade.getOpenedAt(), null
        );
    }

    private BigDecimal currentPrice(PositionFact position, MarketResponse market, MarketPriceFact price,
                                    String accountCurrency) {
        if (market == null || price == null || price.status() != MarketPriceSnapshotStatus.FRESH
                || price.occurredAt() == null) {
            return null;
        }
        if (accountCurrency != null && (market.getQuoteAsset() == null
                || !market.getQuoteAsset().equalsIgnoreCase(accountCurrency))) {
            return null;
        }
        BigDecimal mark = position.side() == TradeType.BUY ? price.bid() : price.ask();
        return mark != null && mark.signum() > 0 ? mark : null;
    }

    private PositionValuationStatus valuationStatus(MarketResponse market, MarketPriceFact price,
                                                     BigDecimal currentPrice, String accountCurrency,
                                                     boolean marketCatalogAvailable) {
        if (market == null) {
            return marketCatalogAvailable ? PositionValuationStatus.UNKNOWN_MARKET
                    : PositionValuationStatus.UNAVAILABLE;
        }
        if (accountCurrency != null && (market.getQuoteAsset() == null
                || !market.getQuoteAsset().equalsIgnoreCase(accountCurrency))) {
            return PositionValuationStatus.UNSUPPORTED_CURRENCY;
        }
        if (currentPrice != null) return PositionValuationStatus.FRESH;
        if (price == null || price.status() == null) return PositionValuationStatus.UNAVAILABLE;
        return switch (price.status()) {
            case FRESH -> PositionValuationStatus.UNAVAILABLE;
            case STALE -> PositionValuationStatus.STALE;
            case UNAVAILABLE -> PositionValuationStatus.UNAVAILABLE;
            case UNKNOWN_MARKET -> PositionValuationStatus.UNKNOWN_MARKET;
        };
    }

    private MarketLookup loadMarkets(List<PositionFact> positions, List<String> warnings) {
        if (positions.isEmpty()) {
            return new MarketLookup(Map.of(), List.of(), true);
        }
        try {
            Map<String, List<MarketResponse>> bySymbol = marketDataClient.findAll().stream()
                    .collect(Collectors.groupingBy(market -> normalize(market.getSymbol())));
            Map<String, MarketResponse> resolved = positions.stream()
                    .map(PositionFact::symbol)
                    .distinct()
                    .filter(symbol -> bySymbol.getOrDefault(normalize(symbol), List.of()).size() == 1)
                    .collect(Collectors.toMap(
                            Function.identity(), symbol -> bySymbol.get(normalize(symbol)).getFirst()
                    ));
            positions.stream()
                    .map(PositionFact::symbol)
                    .filter(symbol -> !resolved.containsKey(symbol))
                    .forEach(symbol -> warnings.add("Marché interne introuvable pour " + symbol));
            return new MarketLookup(
                    resolved,
                    resolved.values().stream().map(MarketResponse::getMarketId).distinct().toList(),
                    true
            );
        } catch (RuntimeException exception) {
            log.warn("Position query market catalog unavailable");
            return new MarketLookup(Map.of(), List.of(), false);
        }
    }

    private Map<UUID, MarketPriceFact> loadPrices(List<UUID> marketIds, List<String> warnings) {
        if (marketIds.isEmpty()) {
            return Map.of();
        }
        try {
            return marketDataClient.findPriceSnapshots(new MarketPriceSnapshotRequest(marketIds))
                    .stream()
                    .map(marketMapper::toFact)
                    .peek(price -> {
                        if (price.status() != MarketPriceSnapshotStatus.FRESH) {
                            warnings.add("Prix indisponible pour le marché " + price.marketId());
                        }
                    })
                    .collect(Collectors.toMap(MarketPriceFact::marketId, Function.identity()));
        } catch (RuntimeException exception) {
            log.warn("Position query market prices unavailable");
            return null;
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.toUpperCase(Locale.ROOT)
                .replace("XBT", "BTC")
                .replaceAll("[^A-Z0-9]", "");
    }

    private record MarketLookup(
            Map<String, MarketResponse> bySymbol,
            List<UUID> marketIds,
            boolean available
    ) {
    }
}
