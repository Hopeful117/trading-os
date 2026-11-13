package com.hope.trading.trading_core.executionauthorization;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionAuthorizationRepository extends JpaRepository<ExecutionAuthorizationEntity, UUID> {
    Optional<ExecutionAuthorizationEntity> findByAgentIdAndExecutionIdempotencyKey(UUID agentId, String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExecutionAuthorizationEntity> findByIdAndAgentId(UUID id, UUID agentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExecutionAuthorizationEntity> findByIdAndHumanUserId(UUID id, UUID userId);
}
