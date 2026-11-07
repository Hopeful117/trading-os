package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentTokenAuthenticationException;
import com.hope.trading.trading_core.agent.api.AgentTokenRequest;
import com.hope.trading.trading_core.agent.api.AgentTokenResponse;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthenticationAuditEventEntity;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthenticationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.security.AgentJwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AgentTokenExchangeService {
    private final AgentCredentialRepository credentials;
    private final AgentIdentityRepository agents;
    private final AgentAuthenticationAuditEventRepository audit;
    private final AgentCredentialGenerator credentialGenerator;
    private final AgentJwtService jwt;
    private final AgentExchangeRateLimiter limiter;
    private final Clock clock;

    public AgentTokenExchangeService(AgentCredentialRepository credentials, AgentIdentityRepository agents,
                                     AgentAuthenticationAuditEventRepository audit,
                                     AgentCredentialGenerator credentialGenerator, AgentJwtService jwt,
                                     AgentExchangeRateLimiter limiter, Clock clock) {
        this.credentials = credentials;
        this.agents = agents;
        this.audit = audit;
        this.credentialGenerator = credentialGenerator;
        this.jwt = jwt;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public AgentTokenResponse exchange(AgentTokenRequest request, String clientKey, String correlationId) {
        Instant now = clock.instant();
        if (request == null || request.credential() == null || request.credential().isBlank()
                || !limiter.allow(clientKey)) {
            record(null, null, null, null, "AGENT_TOKEN_EXCHANGE", "DENIED", "INVALID_CREDENTIAL", correlationId, now);
            throw new AgentTokenAuthenticationException();
        }
        AgentCredential credential = credentials.findByCredentialDigest(credentialGenerator.digest(request.credential()))
                .orElseThrow(() -> failure(null, null, null, null, correlationId, now));
        AgentIdentity agent = agents.findById(credential.agentId())
                .orElseThrow(() -> failure(credential.agentId(), credential.id(), null,
                        credential.credentialVersion(), correlationId, now));
        if (agent.status() != AgentStatus.ACTIVE || !credential.isActiveAt(now)) {
            throw failure(agent.id(), credential.id(), agent.ownerUserId(), credential.credentialVersion(),
                    correlationId, now);
        }
        AgentJwtService.IssuedToken issued = jwt.issue(agent, credential);
        record(agent.id(), credential.id(), agent.ownerUserId(), credential.credentialVersion(),
                "AGENT_TOKEN_EXCHANGE", "ALLOWED", null, correlationId, now);
        return new AgentTokenResponse(issued.value(), "Bearer", issued.expiresAt(), issued.expiresInSeconds());
    }

    private AgentTokenAuthenticationException failure(UUID agentId, UUID credentialId, UUID ownerId,
                                                      Integer version, String correlationId, Instant now) {
        record(agentId, credentialId, ownerId, version, "AGENT_TOKEN_EXCHANGE", "DENIED",
                "INVALID_CREDENTIAL", correlationId, now);
        return new AgentTokenAuthenticationException();
    }

    private void record(UUID agentId, UUID credentialId, UUID ownerId, Integer version, String operation,
                        String outcome, String reason, String correlationId, Instant now) {
        String safeCorrelation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.strip();
        if (safeCorrelation.length() > 128) {
            safeCorrelation = safeCorrelation.substring(0, 128);
        }
        audit.save(AgentAuthenticationAuditEventEntity.of(agentId, credentialId, ownerId, version,
                operation, outcome, reason, safeCorrelation, now));
    }
}
