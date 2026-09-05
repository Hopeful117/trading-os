package com.hope.trading.trading_core.positionclose.domain.repository;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionCloseCommandRepositoryPort {
    Optional<PositionCloseCommand> findById(UUID id);

    Optional<PositionCloseCommand> findByIdempotencyKey(String idempotencyKey);

    List<PositionCloseCommand> findByAccountId(UUID accountId);

    List<PositionCloseCommand> findActiveByBrokerAccountAndScope(UUID brokerAccountId, String resolvedMutationScope);

    PositionCloseCommand save(PositionCloseCommand command);

    void delete(PositionCloseCommand command);
}