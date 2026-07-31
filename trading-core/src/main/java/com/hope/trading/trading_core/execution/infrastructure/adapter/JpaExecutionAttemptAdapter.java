package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.mapper.ExecutionAttemptMapper;
import com.hope.trading.trading_core.execution.infrastructure.persistence.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
@Transactional
public class JpaExecutionAttemptAdapter implements ExecutionAttemptRepositoryPort {
    private final JpaExecutionAttemptRepository repository;
    private final ExecutionAttemptMapper mapper=new ExecutionAttemptMapper();
    public JpaExecutionAttemptAdapter(JpaExecutionAttemptRepository repository){this.repository=repository;}
    @Override public ExecutionAttempt save(ExecutionAttempt attempt){
        ExecutionAttemptEntity entity=repository.findById(attempt.id().value())
                .orElseGet(ExecutionAttemptEntity::new);
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(attempt,entity)));
    }
    @Override @Transactional(readOnly=true) public Optional<ExecutionAttempt> findById(ExecutionAttemptId id){
        return repository.findById(id.value()).map(mapper::toDomain);
    }
    @Override @Transactional(readOnly=true) public List<ExecutionAttempt> findByIntentId(ExecutionIntentId id){
        return repository.findByIntentIdOrderByAttemptNumber(id.value()).stream().map(mapper::toDomain).toList();
    }
    @Override @Transactional(readOnly=true) public Optional<ExecutionAttempt> findLatestByIntentId(ExecutionIntentId id){
        return repository.findFirstByIntentIdOrderByAttemptNumberDesc(id.value()).map(mapper::toDomain);
    }
}
