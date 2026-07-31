package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.mapper.ExecutionIntentMapper;
import com.hope.trading.trading_core.execution.infrastructure.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
@Transactional
public class JpaExecutionIntentAdapter implements ExecutionIntentRepositoryPort {
    private final JpaExecutionIntentRepository repository;
    private final ExecutionIntentMapper mapper = new ExecutionIntentMapper();
    public JpaExecutionIntentAdapter(JpaExecutionIntentRepository repository) { this.repository=repository; }
    @Override public ExecutionIntent save(ExecutionIntent intent) {
        ExecutionIntentEntity entity=repository.findById(intent.id().value())
                .orElseGet(ExecutionIntentEntity::new);
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(intent,entity)));
    }
    @Override @Transactional(readOnly=true) public Optional<ExecutionIntent> findById(ExecutionIntentId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }
    @Override @Transactional(readOnly=true) public Optional<ExecutionIntent> findByIdempotencyKey(IdempotencyKey key) {
        return repository.findByIdempotencyKey(key.value()).map(mapper::toDomain);
    }
    @Override @Transactional(readOnly=true) public List<ExecutionIntent> findByStatuses(Set<ExecutionStatus> statuses) {
        return repository.findByStatusIn(statuses.stream().map(Enum::name).toList())
                .stream().map(mapper::toDomain).toList();
    }
    @Override @Transactional(readOnly=true) public List<ExecutionIntent> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(mapper::toDomain).toList();
    }
    @Override @Transactional(readOnly=true) public List<ExecutionIntent> findByInitiatorId(UUID id) {
        return repository.findByInitiatorIdOrderByCreatedAtDesc(id).stream().map(mapper::toDomain).toList();
    }
}
