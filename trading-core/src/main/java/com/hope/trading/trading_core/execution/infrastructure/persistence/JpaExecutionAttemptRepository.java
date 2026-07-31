package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JpaExecutionAttemptRepository extends JpaRepository<ExecutionAttemptEntity, UUID> {
    List<ExecutionAttemptEntity> findByIntentIdOrderByAttemptNumber(UUID intentId);
    Optional<ExecutionAttemptEntity> findFirstByIntentIdOrderByAttemptNumberDesc(UUID intentId);
}
