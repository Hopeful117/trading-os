package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import java.util.*;

public final class QueryExecutionService {
    private final ExecutionIntentRepositoryPort intents;
    public QueryExecutionService(ExecutionIntentRepositoryPort intents) {
        this.intents = Objects.requireNonNull(intents);
    }
    public Optional<ExecutionIntent> find(ExecutionIntentId id) { return intents.findById(id); }
    public Optional<ExecutionIntent> findOwned(ExecutionIntentId id, UUID initiatorId) {
        return intents.findById(id).filter(i -> i.initiatorId().equals(initiatorId));
    }
    public List<ExecutionIntent> findOwned(UUID initiatorId) {
        return List.copyOf(intents.findByInitiatorId(initiatorId));
    }
    public List<ExecutionIntent> findAll() { return List.copyOf(intents.findAll()); }
}
