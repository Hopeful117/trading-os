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
        Map<String, BigDecimal> balances = account.getBalances().stream()
                .collect(Collectors.toMap(value -> value.getAsset().toUpperCase(),
                        value -> value.getAmount(), BigDecimal::add));
        List<RiskFactsProvider.Position> positions = account.getTrades().stream()
                .filter(trade -> trade.getTradeStatus() == TradeStatus.OPEN)
                .map(this::paperPosition)
                .toList();
        List<RiskFactsProvider.ClosedTrade> closedTrades = account.getTrades().stream()
                .filter(trade -> trade.getTradeStatus() == TradeStatus.CLOSED
                        && trade.getClosedAt() != null
                        && !trade.getClosedAt().isBefore(from)
                        && trade.getClosedAt().isBefore(to))
                .map(this::paperClosedTrade)
                .toList();
        BigDecimal balance = balances.get(account.getBaseCurrency().toUpperCase());
        String payload = writePayload(account, balances, positions, closedTrades);
        List<String> reasons = new java.util.ArrayList<>();
        if (positions.stream().anyMatch(position -> position.protectedQuantity() == null
                || position.protectedQuantity().compareTo(position.signedQuantity().abs()) != 0
                || position.protectiveStops().isEmpty())) {
            reasons.add("PAPER_POSITION_PROTECTION_UNAVAILABLE");
        }
        return new RiskFactsProvider.Snapshot(
                brokerAccount.id(),
                Math.max(1, account.getVersion()),
                clock.instant(),
                reasons.isEmpty(),
                reasons,
                balances,
                new RiskFactsProvider.Account(account.getBaseCurrency(), balance, account.getEquity(), account.getEquity()),
                positions,
                closedTrades,
                List.of(),
                payload);
    }

    private RiskFactsProvider.Position paperPosition(Trade trade) {
        BigDecimal quantity = signedQuantity(trade);
        BigDecimal absoluteQuantity = quantity.abs();
        BigDecimal currentPrice = trade.getCurrentPrice() == null ? trade.getEntryPrice() : trade.getCurrentPrice();
        List<RiskFactsProvider.Stop> stops = trade.getStopLoss() == null ? List.of()
                : List.of(new RiskFactsProvider.Stop("paper-trade-stop:" + trade.getTradeId(),
                        "TRADING_CORE", absoluteQuantity, trade.getStopLoss()));
        return new RiskFactsProvider.Position(trade.getTradeId(), "paper-trade:" + trade.getTradeId(),
                "TRADING_CORE", trade.getSymbol(), quantity, trade.getEntryPrice(),
                currentPrice.multiply(absoluteQuantity), trade.getEntryPrice().multiply(absoluteQuantity),
                trade.getStopLoss() == null ? BigDecimal.ZERO : absoluteQuantity, stops);
    }

    private RiskFactsProvider.ClosedTrade paperClosedTrade(Trade trade) {
        return new RiskFactsProvider.ClosedTrade("paper-trade:" + trade.getTradeId(), trade.getSymbol(),
                accountCurrency(trade), BigDecimal.ZERO, trade.getPnl() == null ? BigDecimal.ZERO : trade.getPnl(),
                trade.getClosedAt());
    }

    private String accountCurrency(Trade trade) {
        return trade.getAccount() == null || trade.getAccount().getBaseCurrency() == null
                ? "USD" : trade.getAccount().getBaseCurrency();
    }

    private BigDecimal signedQuantity(Trade trade) {
        return trade.getType().name().equals("BUY") ? trade.getQuantity() : trade.getQuantity().negate();
    }

    private String writePayload(com.hope.trading.trading_core.model.Account account,
                                Map<String, BigDecimal> balances,
                                List<RiskFactsProvider.Position> positions,
                                List<RiskFactsProvider.ClosedTrade> closedTrades) {
        try {
            return mapper.writeValueAsString(Map.of("accountId", account.getAccountId(),
                    "balances", balances, "positions", positions, "closedTrades", closedTrades));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Paper risk snapshot cannot be preserved", failure);
        }
    }
}
