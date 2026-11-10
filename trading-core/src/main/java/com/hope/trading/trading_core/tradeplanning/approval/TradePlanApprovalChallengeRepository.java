package com.hope.trading.trading_core.tradeplanning.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface TradePlanApprovalChallengeRepository extends JpaRepository<TradePlanApprovalChallengeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TradePlanApprovalChallengeEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TradePlanApprovalChallengeEntity> findByIdAndAgentId(UUID id, UUID agentId);
    Optional<TradePlanApprovalChallengeEntity> findByAgentIdAndIdempotencyKey(UUID agentId, String idempotencyKey);
}
