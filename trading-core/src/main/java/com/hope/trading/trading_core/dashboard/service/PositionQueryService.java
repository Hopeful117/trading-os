package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.integration.BrokerPositionFact;
import com.hope.trading.trading_core.dashboard.integration.MarketDataDashboardMapper;
import com.hope.trading.trading_core.dashboard.integration.MarketPriceFact;
import com.hope.trading.trading_core.dashboard.model.OpenPositionDashboardView;
import com.hope.trading.trading_core.dashboard.model.PositionProtectionStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
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
            List<BrokerPositionFact> brokerPositions,
            BigDecimal equity,
            Instant calculatedAt
    ) {
        if (brokerPositions.isEmpty()) {
            return List.of();
        }

        List<String> warnings = new ArrayList<>();
        MarketLookup marketLookup = loadMarkets(brokerPositions, warnings);
        Map<UUID, MarketPriceFact> prices = loadPrices(marketLookup.marketIds(), warnings);
        Map<UUID, MarketPriceFact> safePrices = prices == null ? Map.of() : prices;

        return buildPositions(accountId, brokerPositions, marketLookup.bySymbol(), safePrices, equity, calculatedAt);
    }

    private List<OpenPositionDashboardView> buildPositions(
            UUID accountId,
            List<BrokerPositionFact> brokerPositions,
            Map<String, MarketResponse> markets,
            Map<UUID, MarketPriceFact> prices,
            BigDecimal equity,
            Instant calculatedAt
    ) {
        return brokerPositions.stream().map(position -> {
            MarketResponse market = markets.get(position.symbol());
            MarketPriceFact price = market == null ? null : prices.get(market.getMarketId());
            BigDecimal currentPrice = price != null
                    && price.status() == MarketPriceSnapshotStatus.FRESH
                    ? price.price() : null;
            PositionValuation value = valuationService.value(position, currentPrice, equity);
            return new OpenPositionDashboardView(
                    position.positionId(), accountId,
                    market == null ? null : market.getMarketId(),
                    position.symbol(), position.side(), position.quantity(),
                    position.entryPrice(), currentPrice, position.stopLoss(), position.takeProfit(),
                    value.pnl(), value.pnlPercentage(), position.brokerUnrealizedPnl(),
                    value.riskAmount(), value.riskPercentage(), value.exposure(),
                    position.stopLoss() == null
                            ? PositionProtectionStatus.MISSING_STOP_LOSS
                            : PositionProtectionStatus.PROTECTED,
                    price != null && price.tradable(),
                    position.openedAt(), price == null ? null : price.occurredAt(), calculatedAt
            );
        }).toList();
    }

    private MarketLookup loadMarkets(List<BrokerPositionFact> positions, List<String> warnings) {
        if (positions.isEmpty()) {
            return new MarketLookup(Map.of(), List.of(), true);
        }
        try {
            Map<String, MarketResponse> bySymbol = marketDataClient.findAll().stream()
                    .collect(Collectors.toMap(
                            market -> normalize(market.getSymbol()),
                            Function.identity(),
                            (first, ignored) -> first
                    ));
            Map<String, MarketResponse> resolved = positions.stream()
                    .map(BrokerPositionFact::symbol)
                    .distinct()
                    .filter(symbol -> bySymbol.containsKey(normalize(symbol)))
                    .collect(Collectors.toMap(
                            Function.identity(), symbol -> bySymbol.get(normalize(symbol))
                    ));
            positions.stream()
                    .map(BrokerPositionFact::symbol)
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
