package com.hope.trading.trading_core.agent.repository;

import com.hope.trading.trading_core.agent.model.AgentIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentIdentityRepository extends JpaRepository<AgentIdentity, UUID> {
    List<AgentIdentity> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<AgentIdentity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select agent from AgentIdentity agent where agent.id = :agentId and agent.ownerUserId = :ownerUserId")
    Optional<AgentIdentity> findForUpdateByIdAndOwnerUserId(@Param("agentId") UUID agentId,
                                                              @Param("ownerUserId") UUID ownerUserId);
}
