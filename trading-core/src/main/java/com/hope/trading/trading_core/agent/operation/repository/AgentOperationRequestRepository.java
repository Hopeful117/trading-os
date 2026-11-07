package com.hope.trading.trading_core.agent.operation.repository;

import com.hope.trading.trading_core.agent.operation.model.AgentOperationRequestEntity;
import com.hope.trading.trading_core.agent.operation.model.AgentOperationStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface AgentOperationRequestRepository extends JpaRepository<AgentOperationRequestEntity, UUID> {
    List<AgentOperationRequestEntity> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<AgentOperationRequestEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<AgentOperationRequestEntity> findByIdAndAgentId(UUID id, UUID agentId);
    boolean existsByOwnerUserIdAndResourceIdAndOperationTypeAndStatusIn(UUID ownerUserId, UUID resourceId,
                                                                         com.hope.trading.trading_core.agent.operation.model.AgentOperationType operationType,
                                                                         Collection<AgentOperationStatus> statuses);

    @Modifying
    @Transactional
    @Query(value = "update agent_operation_request r set status = 'EXECUTING', executed_at = :now "
            + "where r.id = :id and r.agent_id = :agentId and r.status = 'APPROVED' and r.expires_at > :now "
            + "and exists (select 1 from agent_identity a where a.id = r.agent_id and a.status = 'ACTIVE' and a.version = r.agent_version) "
            + "and exists (select 1 from agent_grant g where g.id = r.grant_id and g.agent_id = r.agent_id "
            + "and g.status = 'ACTIVE' and g.active_marker = 1 and g.grant_version = r.grant_version "
            + "and (g.expires_at is null or g.expires_at > :now))", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("agentId") UUID agentId, @Param("now") Instant now);

    @Query("select r from AgentOperationRequestEntity r where r.status = com.hope.trading.trading_core.agent.operation.model.AgentOperationStatus.EXECUTING and r.executedAt < :cutoff")
    List<AgentOperationRequestEntity> findStaleExecuting(@Param("cutoff") Instant cutoff);
}
