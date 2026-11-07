package com.hope.trading.trading_core.agent.repository;

import com.hope.trading.trading_core.agent.model.AgentCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {
    List<AgentCredential> findAllByAgentId(UUID agentId);
    Optional<AgentCredential> findByIdAndAgentId(UUID id, UUID agentId);
    Optional<AgentCredential> findByCredentialDigest(String credentialDigest);
    Optional<AgentCredential> findFirstByAgentIdOrderByCredentialVersionDesc(UUID agentId);

    @Modifying
    @Query(value = "UPDATE agent_credential SET status = 'REVOKED', active_marker = NULL, revoked_at = CURRENT_TIMESTAMP "
            + "WHERE agent_id = :agentId AND status = 'ACTIVE'", nativeQuery = true)
    int revokeActiveByAgentId(@Param("agentId") UUID agentId);
}
