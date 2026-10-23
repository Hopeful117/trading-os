package com.hope.trading.trading_core.valuation.application;

import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.service.TradingCalculatorService;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns PAPER CASH derivative valuation orchestration, not Risk calculations. */
@Service
public class PaperDerivativeAccountValuationService {
    private final MarketDataClient marketDataClient;
    private final AccountRepository accounts;
    private final TradingCalculatorService calculator;
    private final Clock clock;

    public PaperDerivativeAccountValuationService(MarketDataClient marketDataClient,
                                                  AccountRepository accounts,
                                                  TradingCalculatorService calculator,
                                                  Clock clock) {
        this.marketDataClient = marketDataClient;
        this.accounts = accounts;
        this.calculator = calculator;
        this.clock = clock;
    }

    public ValuationResult refresh(Account account) {
        requireCash(account);
        BigDecimal realizedBalance = account.getRealizedBalance() == null
                ? account.getEquity() : account.getRealizedBalance();
        List<Trade> openTrades = openTrades(account);
        Instant valuedAt = clock.instant();
        if (openTrades.isEmpty()) {
            account.setRealizedBalance(realizedBalance);
            apply(account, BigDecimal.ZERO, valuedAt, null, AccountValuationStatus.FRESH);
            accounts.save(account);
            return result(account, BigDecimal.ZERO, valuedAt, null, null);
        }

        Map<String, MarketResponse> markets = resolveMarkets(openTrades);
        List<java.util.UUID> marketIds = markets.values().stream()
                .map(MarketResponse::getMarketId).distinct().toList();
        Map<java.util.UUID, MarketPriceSnapshotDto> prices = marketDataClient
                .findPriceSnapshots(new com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest(marketIds))
                .stream().collect(Collectors.toMap(MarketPriceSnapshotDto::marketId, Function.identity()));

        BigDecimal unrealized = BigDecimal.ZERO;
        Instant latestMarketData = null;
        Map<Trade, BigDecimal> valuationPrices = new LinkedHashMap<>();
        for (Trade trade : openTrades) {
            MarketResponse market = markets.get(normalize(trade.getSymbol()));
            MarketPriceSnapshotDto snapshot = prices.get(market.getMarketId());
            validateSnapshot(trade, snapshot);
            BigDecimal valuationPrice = midpoint(snapshot.bid(), snapshot.ask());
            valuationPrices.put(trade, valuationPrice);
            unrealized = unrealized.add(calculator.calculatePnL(
                    trade.getType(), trade.getEntryPrice(), valuationPrice, trade.getQuantity()));
            if (latestMarketData == null || snapshot.occurredAt().isAfter(latestMarketData)) {
                latestMarketData = snapshot.occurredAt();
            }
        }

        account.setRealizedBalance(realizedBalance);
        valuationPrices.forEach((trade, price) -> trade.setCurrentPrice(price));
        apply(account, unrealized, valuedAt, latestMarketData, AccountValuationStatus.FRESH);
        accounts.save(account);
        return result(account, unrealized, valuedAt, latestMarketData, null);
    }

    /** Recomputes from persisted trade marks after settlement without a market call. */
    public ValuationResult recomputeKnownMarks(Account account, Instant valuedAt) {
        requireCash(account);
        ensureBaseline(account);
        BigDecimal unrealized = openTrades(account).stream()
                .map(trade -> calculator.calculatePnL(trade.getType(), trade.getEntryPrice(),
                        trade.getCurrentPrice() == null ? trade.getEntryPrice() : trade.getCurrentPrice(),
                        trade.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        AccountValuationStatus status = openTrades(account).isEmpty()
                ? AccountValuationStatus.FRESH : AccountValuationStatus.STALE;
        apply(account, unrealized, valuedAt, account.getMarketDataTimestamp(), status);
        return result(account, unrealized, valuedAt, account.getMarketDataTimestamp(), null);
    }

    public void ensureBaseline(Account account) {
        if (account.getRealizedBalance() == null) {
            account.setRealizedBalance(account.getEquity());
        }
    }

    private Map<String, MarketResponse> resolveMarkets(List<Trade> trades) {
        List<MarketResponse> catalogue = marketDataClient.findAll();
        return trades.stream().map(Trade::getSymbol).distinct().collect(Collectors.toMap(
                this::normalize,
                symbol -> {
                    List<MarketResponse> matches = catalogue.stream()
                            .filter(market -> market != null && market.getSymbol() != null
                                    && symbol.equalsIgnoreCase(market.getSymbol())
                                    && market.getProductType() == InstrumentProductType.LINEAR_PERPETUAL)
                            .toList();
                    if (matches.size() != 1) {
                        throw new IllegalStateException("PAPER derivative market is unavailable or ambiguous");
                    }
                    return matches.getFirst();
                }));
    }

    private void validateSnapshot(Trade trade, MarketPriceSnapshotDto snapshot) {
        if (snapshot == null || snapshot.status() != MarketPriceSnapshotStatus.FRESH
                || snapshot.occurredAt() == null || snapshot.bid() == null || snapshot.ask() == null
                || snapshot.bid().signum() <= 0 || snapshot.ask().signum() <= 0
                || snapshot.ask().compareTo(snapshot.bid()) < 0) {
            throw new IllegalStateException("PAPER derivative valuation requires fresh bid/ask data for "
                    + trade.getSymbol());
        }
    }

    private BigDecimal midpoint(BigDecimal bid, BigDecimal ask) {
        return bid.add(ask).divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
    }

    private void apply(Account account, BigDecimal unrealized, Instant valuedAt,
                       Instant marketDataTimestamp, AccountValuationStatus status) {
        account.setEquity(account.getRealizedBalance().add(unrealized));
        account.setValuationStatus(status);
        account.setValuedAt(valuedAt);
        account.setMarketDataTimestamp(marketDataTimestamp);
        if (status == AccountValuationStatus.FRESH
                && account.getEquity().compareTo(account.getPeakEquity()) > 0) {
            account.setPeakEquity(account.getEquity());
        }
    }

    private ValuationResult result(Account account, BigDecimal unrealized, Instant valuedAt,
                                   Instant marketDataTimestamp, String warning) {
        return new ValuationResult(account.getRealizedBalance(), unrealized, account.getEquity(),
                account.getValuationStatus(), valuedAt, marketDataTimestamp, warning);
    }

    private List<Trade> openTrades(Account account) {
        return account.getTrades() == null ? List.of() : account.getTrades().stream()
                .filter(Objects::nonNull)
                .filter(trade -> trade.getTradeStatus() == TradeStatus.OPEN)
                .toList();
    }

    private void requireCash(Account account) {
        if (account == null || account.getSettlementModel() != SettlementModel.CASH) {
            throw new IllegalArgumentException("PAPER derivative valuation requires a CASH account");
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record ValuationResult(BigDecimal realizedBalance, BigDecimal unrealizedPnl,
                                  BigDecimal equity, AccountValuationStatus status,
                                  Instant valuedAt, Instant marketDataTimestamp, String warning) {
        public boolean fresh() {
            return status == AccountValuationStatus.FRESH;
        }
    }
}
