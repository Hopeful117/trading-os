package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public final class ModeAwareRiskFactsProvider implements RiskFactsProvider {
    private final BrokerRiskFactsPort liveFacts;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper) {
        this(liveFacts, mapper, Clock.systemUTC());
    }

    @Autowired
    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper, Clock clock) {
        this.liveFacts = liveFacts;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public RiskFactsProvider.Snapshot load(com.hope.trading.trading_core.model.Account account,
                                           BrokerAccount brokerAccount, UUID sourceId,
                                             Instant from, Instant to) {
        if (brokerAccount.executionMode() == ExecutionMode.LIVE) {
            return RiskFactsProvider.fromBroker(liveFacts.load(sourceId, from, to));
        }
        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            return paperSnapshot(account, brokerAccount, from, to);
        }
        throw new IllegalStateException("Execution mode is unavailable");
    }

    private RiskFactsProvider.Snapshot paperSnapshot(com.hope.trading.trading_core.model.Account account,
                                                       BrokerAccount brokerAccount,
                                                         Instant from, Instant to) {
        List<String> reasons = new java.util.ArrayList<>();
        if (account == null || account.getAccountId() == null || blank(account.getBaseCurrency())) {
            reasons.add("PAPER_ACCOUNT_FACTS_INVALID");
        }
        String valuationAsset = account == null || blank(account.getBaseCurrency())
                ? null : account.getBaseCurrency().toUpperCase(Locale.ROOT);
        List<com.hope.trading.trading_core.model.AccountBalance> accountBalances = account == null
                || account.getBalances() == null ? List.of() : account.getBalances();
        Map<String, BigDecimal> balances = accountBalances.stream()
                .filter(value -> value != null && !blank(value.getAsset()) && value.getAmount() != null)
                .collect(Collectors.toMap(value -> value.getAsset().toUpperCase(Locale.ROOT),
                        value -> value.getAmount(), BigDecimal::add));
        if (accountBalances.stream().anyMatch(value -> value == null || blank(value.getAsset())
                || value.getAmount() == null)) {
            reasons.add("PAPER_BALANCE_FACTS_INVALID");
        }
        List<Trade> trades = account == null || account.getTrades() == null ? List.of() : account.getTrades();
        List<RiskFactsProvider.Position> positions = trades.stream()
                .filter(trade -> trade != null && trade.getTradeStatus() == TradeStatus.OPEN)
                .map(this::paperPosition)
                .toList();
        List<RiskFactsProvider.ClosedTrade> closedTrades = trades.stream()
                .filter(trade -> trade != null && trade.getTradeStatus() == TradeStatus.CLOSED
                        && trade.getClosedAt() != null
                        && !trade.getClosedAt().isBefore(from)
                        && trade.getClosedAt().isBefore(to))
                .map(trade -> paperClosedTrade(trade, valuationAsset))
                .toList();
        if (trades.stream().anyMatch(this::invalidTradeFacts)) {
            reasons.add("PAPER_TRADE_FACTS_INVALID");
        }
        BigDecimal balance = valuationAsset == null ? null : balances.get(valuationAsset);
        if (balance == null || account == null || account.getEquity() == null) {
            reasons.add("PAPER_ACCOUNT_VALUATION_INCOMPLETE");
        }
        String payload = writePayload(account, balances, positions, closedTrades);
        reasons.add("PAPER_MARGIN_UNAVAILABLE");
        return new RiskFactsProvider.Snapshot(
                brokerAccount.id(),
                sourceVersion(account, trades),
                clock.instant(),
                reasons.isEmpty(),
                reasons,
                balances,
                new RiskFactsProvider.Account(valuationAsset, balance, account == null ? null : account.getEquity(),
                        null, account == null ? null : account.getStartingBalance()),
                positions,
                closedTrades,
                List.of(),
                payload);
    }

    private RiskFactsProvider.Position paperPosition(Trade trade) {
        BigDecimal quantity = signedQuantity(trade);
        BigDecimal absoluteQuantity = quantity == null ? null : quantity.abs();
        BigDecimal currentPrice = trade.getCurrentPrice() == null ? trade.getEntryPrice() : trade.getCurrentPrice();
        List<RiskFactsProvider.Stop> stops = trade.getStopLoss() == null || absoluteQuantity == null ? List.of()
                : List.of(new RiskFactsProvider.Stop("paper-trade-stop:" + trade.getTradeId(),
                        "TRADING_CORE", absoluteQuantity, trade.getStopLoss()));
        return new RiskFactsProvider.Position(trade.getTradeId(), "paper-trade:" + trade.getTradeId(),
                "TRADING_CORE", trade.getSymbol(), quantity, trade.getEntryPrice(),
                currentPrice == null || absoluteQuantity == null ? null : currentPrice.multiply(absoluteQuantity), null,
                trade.getStopLoss() == null ? BigDecimal.ZERO : absoluteQuantity, stops);
    }

    private RiskFactsProvider.ClosedTrade paperClosedTrade(Trade trade, String settlementAsset) {
        return new RiskFactsProvider.ClosedTrade("paper-trade:" + trade.getTradeId(), trade.getSymbol(),
                settlementAsset, BigDecimal.ZERO, trade.getPnl() == null ? BigDecimal.ZERO : trade.getPnl(),
                trade.getClosedAt());
    }

    private BigDecimal signedQuantity(Trade trade) {
        if (trade == null || trade.getType() == null || trade.getQuantity() == null) return null;
        return trade.getType().name().equals("BUY") ? trade.getQuantity() : trade.getQuantity().negate();
    }

    private String writePayload(com.hope.trading.trading_core.model.Account account,
                                Map<String, BigDecimal> balances,
                                List<RiskFactsProvider.Position> positions,
                                List<RiskFactsProvider.ClosedTrade> closedTrades) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("accountId", account == null ? null : account.getAccountId());
            payload.put("accountVersion", account == null ? null : account.getVersion());
            payload.put("tradeVersions", account == null || account.getTrades() == null ? Map.of()
                    : account.getTrades().stream().filter(trade -> trade != null && trade.getTradeId() != null)
                            .collect(Collectors.toMap(Trade::getTradeId, Trade::getVersion)));
            payload.put("balances", balances);
            payload.put("positions", positions);
            payload.put("closedTrades", closedTrades);
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Paper risk snapshot cannot be preserved", failure);
        }
    }

    private long sourceVersion(com.hope.trading.trading_core.model.Account account, List<Trade> trades) {
        long accountVersion = account == null ? 0 : account.getVersion();
        long tradeVersion = trades.stream().filter(java.util.Objects::nonNull)
                .mapToLong(Trade::getVersion).max().orElse(0);
        return Math.max(accountVersion, tradeVersion);
    }

    private boolean invalidTradeFacts(Trade trade) {
        return trade == null || trade.getTradeId() == null || blank(trade.getSymbol())
                || trade.getType() == null || trade.getQuantity() == null
                || trade.getQuantity().signum() <= 0 || trade.getEntryPrice() == null
                || trade.getEntryPrice().signum() <= 0 || trade.getTradeStatus() == null
                || (trade.getCurrentPrice() != null && trade.getCurrentPrice().signum() <= 0)
                || (trade.getStopLoss() != null && trade.getStopLoss().signum() <= 0)
                || (trade.getTradeStatus() == TradeStatus.CLOSED
                    && (trade.getClosedAt() == null || trade.getPnl() == null));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
