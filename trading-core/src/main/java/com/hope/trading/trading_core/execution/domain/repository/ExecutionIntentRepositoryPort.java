package com.hope.trading.trading_core.execution.domain.repository;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.util.*;

public interface ExecutionIntentRepositoryPort {
    ExecutionIntent save(ExecutionIntent intent);
    Optional<ExecutionIntent> findById(ExecutionIntentId id);
    Optional<ExecutionIntent> findByIdempotencyKey(IdempotencyKey key);
    List<ExecutionIntent> findByStatuses(Set<ExecutionStatus> statuses);
    List<ExecutionIntent> findByInitiatorId(UUID initiatorId);
    List<ExecutionIntent> findAll();
}
