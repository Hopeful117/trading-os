package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.repository.PositionCloseCommandRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaPositionCloseCommandRepositoryAdapter implements PositionCloseCommandRepositoryPort {
    private final JpaPositionCloseCommandRepository repository;

    public JpaPositionCloseCommandRepositoryAdapter(JpaPositionCloseCommandRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PositionCloseCommand> findById(UUID id) {
        return repository.findById(id).map(PositionCloseCommandMapper::toDomain);
    }

    @Override
    public Optional<PositionCloseCommand> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(PositionCloseCommandMapper::toDomain);
    }

    @Override
    public List<PositionCloseCommand> findByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).stream()
                .map(PositionCloseCommandMapper::toDomain)
                .toList();
    }

    @Override
    public List<PositionCloseCommand> findActiveByBrokerAccountAndScope(UUID brokerAccountId, String resolvedMutationScope) {
        return repository.findActiveByBrokerAccountAndScope(brokerAccountId, resolvedMutationScope).stream()
                .map(PositionCloseCommandMapper::toDomain)
                .toList();
    }

    @Override
    public PositionCloseCommand save(PositionCloseCommand command) {
        PositionCloseCommandEntity entity = PositionCloseCommandMapper.toEntity(command);
        PositionCloseCommandEntity saved = repository.save(entity);
        return PositionCloseCommandMapper.toDomain(saved);
    }

    @Override
    public void delete(PositionCloseCommand command) {
        PositionCloseCommandEntity entity = PositionCloseCommandMapper.toEntity(command);
        repository.delete(entity);
    }
}