package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;

public final class TradeOutcomeService {
    private final TradeOutcomeRepositoryPort outcomes;
    private final java.util.function.Supplier<UUID> ids;
    private final AccountRepository accounts;

    public TradeOutcomeService(TradeOutcomeRepositoryPort outcomes, java.util.function.Supplier<UUID> ids) {
        this(outcomes, ids, null);
    }

    public TradeOutcomeService(TradeOutcomeRepositoryPort outcomes, java.util.function.Supplier<UUID> ids,
                               AccountRepository accounts) {
        this.outcomes = outcomes; this.ids = ids; this.accounts = accounts;
    }

    public TradeOutcome acknowledge(ExecutionIntent intent, BrokerOrder order, Instant now) {
        TradeOutcome existing = outcomes.findByExecutionIntentId(intent.id().value()).orElse(null);
        if (existing != null) return existing;
        UUID accountId = intent.accountId();
        if (accountId == null && accounts != null) {
            accountId = accounts.findByBrokerAccountId(intent.brokerAccountId())
                    .map(com.hope.trading.trading_core.model.Account::getAccountId).orElse(null);
        }
        if (accountId == null) {
            throw new IllegalStateException("Cannot create TradeOutcome without account identity");
        }
        try {
            BigDecimal entryPrice = averageFillPrice(order);
            TradeOutcome result = TradeOutcome.acknowledged(ids.get(), accountId,
                    intent.brokerAccountId(), intent.id().value(), intent.tradePlan().tradePlanId(),
                    intent.tradePlan().version(), intent.provenance(), intent.parameters(), entryPrice, now);
            return outcomes.save(result);
        } catch (DataIntegrityViolationException race) {
            return outcomes.findByExecutionIntentId(intent.id().value()).orElseThrow(() -> race);
        }
    }

    public List<TradeOutcome> findByAccount(UUID accountId) { return outcomes.findByAccountId(accountId); }
    public List<TradeOutcome> findByAccount(UUID accountId, UUID strategyId, String instrument,
                                            com.hope.trading.trading_core.execution.domain.valueobject.TradeOutcomeStatus status,
                                            Instant from, Instant to) {
        return outcomes.findByAccountId(accountId).stream()
                .filter(item -> strategyId == null || item.provenance().stream()
                        .anyMatch(provenance -> strategyId.equals(provenance.strategyId())))
                .filter(item -> instrument == null || item.instrument().equalsIgnoreCase(instrument))
                .filter(item -> status == null || item.status() == status)
                .filter(item -> from == null || !item.createdAt().isBefore(from))
                .filter(item -> to == null || item.createdAt().isBefore(to))
                .toList();
    }
    public TradeOutcome find(UUID id) { return outcomes.findById(id).orElseThrow(); }

    private static BigDecimal averageFillPrice(BrokerOrder order) {
        if (order.fills().isEmpty()) return null;
        BigDecimal quantity = BigDecimal.ZERO, weighted = BigDecimal.ZERO;
        for (BrokerOrder.Fill fill : order.fills()) {
            quantity = quantity.add(fill.quantity());
            weighted = weighted.add(fill.quantity().multiply(fill.price()));
        }
        return quantity.signum() == 0 ? null : weighted.divide(quantity, 12, java.math.RoundingMode.HALF_UP);
    }
}
