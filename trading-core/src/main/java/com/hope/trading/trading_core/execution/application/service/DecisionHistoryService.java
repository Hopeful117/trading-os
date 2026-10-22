package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.api.dto.DecisionHistoryItemDto;
import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public class DecisionHistoryService {
    private final TradeOutcomeRepositoryPort outcomes;
    private final AccountRepository accounts;
    private final QueryExecutionService executions;

    public DecisionHistoryService(TradeOutcomeRepositoryPort outcomes,
                                  AccountRepository accounts,
                                  QueryExecutionService executions) {
        this.outcomes = outcomes;
        this.accounts = accounts;
        this.executions = executions;
    }

    @Transactional(readOnly = true)
    public List<DecisionHistoryItemDto> findByAccount(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow();
        return outcomes.findByAccountId(accountId).stream()
                .map(outcome -> toDto(outcome, account))
                .toList();
    }

    private DecisionHistoryItemDto toDto(TradeOutcome outcome, Account account) {
        Trade trade = outcome.tradeId() == null ? null : account.getTrades().stream()
                .filter(candidate -> outcome.tradeId().equals(candidate.getTradeId()))
                .findFirst()
                .orElse(null);
        var execution = executions.find(new ExecutionIntentId(outcome.executionIntentId()));
        boolean lineageAvailable = outcome.tradeId() != null && trade != null;
        UUID verifiedTradeId = lineageAvailable ? trade.getTradeId() : null;

        return new DecisionHistoryItemDto(
                outcome.id(),
                outcome.executionIntentId(),
                verifiedTradeId,
                outcome.tradePlanId(),
                outcome.tradePlanVersion(),
                execution.map(intent -> intent.riskApproval() == null
                        ? null : intent.riskApproval().evaluationId()).orElse(null),
                outcome.instrument(),
                outcome.side().name(),
                outcome.quantity(),
                outcome.entryPrice(),
                outcome.stopLossPrice(),
                outcome.expectedMonetaryRisk(),
                outcome.riskRewardRatio(),
                trade == null ? null : trade.getEntryPrice(),
                trade == null ? null : trade.getPnl(),
                outcome.actualFee(),
                outcome.status(),
                execution.map(intent -> intent.status()).orElse(null),
                trade == null ? null : trade.getTradeStatus(),
                trade == null ? null : trade.getOpenedAt(),
                trade == null ? (outcome.tradeId() == null ? outcome.closedAt() : null) : trade.getClosedAt(),
                lineageAvailable,
                trade != null && trade.getClosedAt() != null && trade.getPnl() != null,
                outcome.createdAt());
    }
}
