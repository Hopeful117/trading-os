package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentCredentialResponse;
import com.hope.trading.trading_core.agent.api.AgentRequest;
import com.hope.trading.trading_core.agent.api.AgentResponse;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuditEventEntity;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentLifecycleService {
    private final AgentIdentityRepository agents;
    private final AgentCredentialRepository credentials;
    private final AgentAuditEventRepository auditEvents;
    private final AgentCredentialGenerator credentialGenerator;
    private final Clock clock;

    @Transactional
    public AgentResponse register(UUID ownerUserId, AgentRequest request, String correlationId) {
        requireOwner(ownerUserId);
        Instant now = clock.instant();
        AgentIdentity agent = AgentIdentity.register(ownerUserId, request.displayName(), request.agentType(), now);
        agents.save(agent);
        audit(agent, null, "AGENT_REGISTERED", "ALLOWED", null, correlationId, now);
        return map(agent);
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(UUID ownerUserId) {
        requireOwner(ownerUserId);
        return agents.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse get(UUID ownerUserId, UUID agentId) {
        return map(requireOwned(ownerUserId, agentId));
    }

    @Transactional
    public void suspend(UUID ownerUserId, UUID agentId, String correlationId) {
        AgentIdentity agent = requireOwnedForUpdate(ownerUserId, agentId);
        Instant now = clock.instant();
        agent.suspend(now);
        credentials.revokeActiveByAgentId(agent.id());
        audit(agent, null, "AGENT_SUSPENDED", "ALLOWED", null, correlationId, now);
    }

    @Transactional
    public void activate(UUID ownerUserId, UUID agentId, String correlationId) {
        AgentIdentity agent = requireOwnedForUpdate(ownerUserId, agentId);
        Instant now = clock.instant();
        agent.activate(now);
        audit(agent, null, "AGENT_ACTIVATED", "ALLOWED", null, correlationId, now);
    }

    @Transactional
    public void revoke(UUID ownerUserId, UUID agentId, String correlationId) {
        AgentIdentity agent = requireOwnedForUpdate(ownerUserId, agentId);
        Instant now = clock.instant();
        agent.revoke(now);
        credentials.revokeActiveByAgentId(agent.id());
        audit(agent, null, "AGENT_REVOKED", "ALLOWED", null, correlationId, now);
    }

    @Transactional
    public AgentCredentialResponse issueCredential(UUID ownerUserId, UUID agentId, String correlationId) {
        AgentIdentity agent = requireOwnedForUpdate(ownerUserId, agentId);
        if (agent.status() != AgentStatus.ACTIVE) {
            throw new AgentLifecycleException("Only an active agent can issue a credential");
        }
        Instant now = clock.instant();
        credentials.revokeActiveByAgentId(agent.id());
        int nextVersion = credentials.findFirstByAgentIdOrderByCredentialVersionDesc(agent.id())
                .map(value -> value.credentialVersion() + 1)
                .orElse(1);
        AgentCredentialGenerator.Material material = credentialGenerator.generate();
        AgentCredential credential = AgentCredential.issue(agent.id(), material.digest(), material.fingerprint(),
                nextVersion, now, null);
        credentials.save(credential);
        audit(agent, credential, "CREDENTIAL_ISSUED", "ALLOWED", null, correlationId, now);
        return new AgentCredentialResponse(credential.id(), agent.id(), material.raw(), credential.credentialFingerprint(),
                credential.credentialVersion(), credential.status(), credential.createdAt(), credential.expiresAt());
    }

    @Transactional
    public void revokeCredential(UUID ownerUserId, UUID agentId, UUID credentialId, String correlationId) {
        AgentIdentity agent = requireOwnedForUpdate(ownerUserId, agentId);
        AgentCredential credential = credentials.findByIdAndAgentId(credentialId, agent.id())
                .orElseThrow(AgentNotFoundException::new);
        Instant now = clock.instant();
        credential.revoke(now);
        audit(agent, credential, "CREDENTIAL_REVOKED", "ALLOWED", null, correlationId, now);
    }

    private AgentIdentity requireOwned(UUID ownerUserId, UUID agentId) {
        requireOwner(ownerUserId);
        if (agentId == null) {
            throw new AgentNotFoundException();
        }
        return agents.findByIdAndOwnerUserId(agentId, ownerUserId).orElseThrow(AgentNotFoundException::new);
    }

    private AgentIdentity requireOwnedForUpdate(UUID ownerUserId, UUID agentId) {
        requireOwner(ownerUserId);
        if (agentId == null) {
            throw new AgentNotFoundException();
        }
        return agents.findForUpdateByIdAndOwnerUserId(agentId, ownerUserId)
                .orElseThrow(AgentNotFoundException::new);
    }

    private void requireOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new AgentNotFoundException();
        }
    }

    private void audit(AgentIdentity agent, AgentCredential credential, String operation, String outcome,
                       String reasonCode, String correlationId, Instant now) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.strip();
        if (effectiveCorrelationId.length() > 128) {
            throw new AgentLifecycleException("Correlation ID is too long");
        }
        auditEvents.save(AgentAuditEventEntity.of(agent.id(), agent.ownerUserId(),
                credential == null ? null : credential.id(), operation, outcome, reasonCode,
                effectiveCorrelationId, now));
    }

    private AgentResponse map(AgentIdentity agent) {
        return new AgentResponse(agent.id(), agent.ownerUserId(), agent.displayName(), agent.agentType(),
                agent.status(), agent.createdAt(), agent.updatedAt());
    }
}
