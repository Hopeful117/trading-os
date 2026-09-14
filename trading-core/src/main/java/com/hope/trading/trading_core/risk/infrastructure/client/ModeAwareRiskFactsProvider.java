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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class ModeAwareRiskFactsProvider implements RiskFactsProvider {
    private final BrokerRiskFactsPort liveFacts;
    private final ObjectMapper mapper;

    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper) {
        this.liveFacts = liveFacts;
        this.mapper = mapper;
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
        return new RiskFactsProvider.Snapshot(
                brokerAccount.id(),
                account.getTrades().size(),
                Instant.now(),
                false,
                List.of("PAPER_RISK_LEDGER_UNAVAILABLE", "PAPER_MARGIN_UNAVAILABLE",
                        "PAPER_POSITION_PROTECTION_UNAVAILABLE"),
                balances,
                new RiskFactsProvider.Account(account.getBaseCurrency(), balance, account.getEquity(), null),
                positions,
                closedTrades,
                List.of(),
                payload);
    }

    private RiskFactsProvider.Position paperPosition(Trade trade) {
        return new RiskFactsProvider.Position(trade.getTradeId(), "paper-trade:" + trade.getTradeId(),
                "TRADING_CORE", trade.getSymbol(), signedQuantity(trade), trade.getEntryPrice(),
                null, null, BigDecimal.ZERO, List.of());
    }

    private RiskFactsProvider.ClosedTrade paperClosedTrade(Trade trade) {
        return new RiskFactsProvider.ClosedTrade("paper-trade:" + trade.getTradeId(), trade.getSymbol(),
                null, null, trade.getPnl(), trade.getClosedAt());
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
