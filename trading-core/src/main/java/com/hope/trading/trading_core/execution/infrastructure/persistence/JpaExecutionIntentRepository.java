package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JpaExecutionIntentRepository extends JpaRepository<ExecutionIntentEntity, UUID> {
    Optional<ExecutionIntentEntity> findByIdempotencyKey(String key);
    List<ExecutionIntentEntity> findByStatusIn(Collection<String> statuses);
    List<ExecutionIntentEntity> findByInitiatorIdOrderByCreatedAtDesc(UUID initiatorId);
    List<ExecutionIntentEntity> findAllByOrderByCreatedAtDesc();
}
