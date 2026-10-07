package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTradeOutcomeRepository extends JpaRepository<TradeOutcomeEntity, UUID> {
    Optional<TradeOutcomeEntity> findByExecutionIntentId(UUID executionIntentId);
    List<TradeOutcomeEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
