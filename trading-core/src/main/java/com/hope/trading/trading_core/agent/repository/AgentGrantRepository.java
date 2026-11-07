package com.hope.trading.trading_core.agent.repository;

import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentGrantStatus;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentGrantRepository extends JpaRepository<AgentGrant, UUID> {
    List<AgentGrant> findAllByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Optional<AgentGrant> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<AgentGrant> findByAgentIdAndPermissionAndResourceTypeAndResourceIdAndStatus(
            UUID agentId, AgentPermission permission, AgentResourceType resourceType,
            UUID resourceId, AgentGrantStatus status);

    @Modifying
    @Transactional
    @Query("update AgentGrant grant set grant.status = com.hope.trading.trading_core.agent.model.AgentGrantStatus.EXPIRED, "
            + "grant.activeMarker = null where grant.status = "
            + "com.hope.trading.trading_core.agent.model.AgentGrantStatus.ACTIVE "
            + "and grant.expiresAt is not null and grant.expiresAt <= :now")
    int expireElapsed(@Param("now") Instant now);

    @Query("select grant from AgentGrant grant where grant.agentId = :agentId "
            + "and grant.permission = :permission and grant.resourceType = :resourceType "
            + "and grant.resourceId = :resourceId and grant.status = "
            + "com.hope.trading.trading_core.agent.model.AgentGrantStatus.ACTIVE "
            + "and (grant.expiresAt is null or grant.expiresAt > :now)")
    Optional<AgentGrant> findEffective(@Param("agentId") UUID agentId,
                                       @Param("permission") AgentPermission permission,
                                       @Param("resourceType") AgentResourceType resourceType,
                                       @Param("resourceId") UUID resourceId,
                                       @Param("now") Instant now);
}
