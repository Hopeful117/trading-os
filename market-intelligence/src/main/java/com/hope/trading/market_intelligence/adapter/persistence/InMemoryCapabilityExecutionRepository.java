package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.CapabilityExecutionRepository;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;

import java.util.*;
import java.util.concurrent.*;

public class InMemoryCapabilityExecutionRepository implements CapabilityExecutionRepository {
    private final ConcurrentMap<UUID, CapabilityExecution> executions = new ConcurrentHashMap<>();
    @Override public CapabilityExecution save(CapabilityExecution execution) {
        executions.put(execution.id(), execution);
        return execution;
    }
    @Override public List<CapabilityExecution> findByAnalysisExecutionId(UUID id) {
        return executions.values().stream()
                .filter(execution -> execution.analysisExecutionId().equals(id))
                .sorted(Comparator.comparing(CapabilityExecution::createdAt)).toList();
    }
}
