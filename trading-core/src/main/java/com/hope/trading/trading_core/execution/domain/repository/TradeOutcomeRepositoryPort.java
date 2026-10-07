package com.hope.trading.trading_core.execution.domain.repository;

import com.hope.trading.trading_core.execution.domain.aggregate.TradeOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeOutcomeRepositoryPort {
    TradeOutcome save(TradeOutcome outcome);
    Optional<TradeOutcome> findByExecutionIntentId(UUID executionIntentId);
    List<TradeOutcome> findByAccountId(UUID accountId);
    Optional<TradeOutcome> findById(UUID id);
}
