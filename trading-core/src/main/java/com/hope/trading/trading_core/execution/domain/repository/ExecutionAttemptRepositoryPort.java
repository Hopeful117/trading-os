package com.hope.trading.trading_core.execution.domain.repository;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.util.*;

public interface ExecutionAttemptRepositoryPort {
    ExecutionAttempt save(ExecutionAttempt attempt);
    Optional<ExecutionAttempt> findById(ExecutionAttemptId id);
    List<ExecutionAttempt> findByIntentId(ExecutionIntentId intentId);
    Optional<ExecutionAttempt> findLatestByIntentId(ExecutionIntentId intentId);
}
