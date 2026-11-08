package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentGrantRequest;
import com.hope.trading.trading_core.agent.api.AgentGrantResponse;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventEntity;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentGrantStatus;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentGrantRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.agent.repository.ResearchScopeRepository;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentGrantService {
    private final AgentIdentityRepository agents;
    private final AgentGrantRepository grants;
    private final BrokerAccountRepository brokerAccounts;
    private final MarketDataClient markets;
    private final ResearchScopeRepository researchScopes;
    private final AgentAuthorizationAuditEventRepository auditEvents;
    private final Clock clock;

    public AgentGrantService(AgentIdentityRepository agents, AgentGrantRepository grants,
                             BrokerAccountRepository brokerAccounts, MarketDataClient markets,
                             AgentAuthorizationAuditEventRepository auditEvents, Clock clock) {
        this(agents, grants, brokerAccounts, markets, null, auditEvents, clock);
    }

    @Autowired
    public AgentGrantService(AgentIdentityRepository agents, AgentGrantRepository grants,
                             BrokerAccountRepository brokerAccounts, MarketDataClient markets,
                             ResearchScopeRepository researchScopes,
                             AgentAuthorizationAuditEventRepository auditEvents, Clock clock) {
        this.agents = agents;
        this.grants = grants;
        this.brokerAccounts = brokerAccounts;
        this.markets = markets;
        this.researchScopes = researchScopes;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Transactional
    public AgentGrantResponse create(UUID ownerUserId, UUID agentId, AgentGrantRequest request, String correlationId) {
        var agent = ownedAgentForUpdate(ownerUserId, agentId);
        if (agent.status() != AgentStatus.ACTIVE) {
            throw conflict("INACTIVE_AGENT", "Grants can only be created for active agents");
        }
        Instant now = clock.instant();
        validateRequest(ownerUserId, request, now);
        expireElapsed(now);
        if (grants.findByAgentIdAndPermissionAndResourceTypeAndResourceIdAndStatus(
                agentId, request.permission(), request.resourceType(), request.resourceId(), AgentGrantStatus.ACTIVE)
                .isPresent()) {
            throw conflict("DUPLICATE_ACTIVE_GRANT", "An equivalent active grant already exists");
        }
        AgentGrant grant = AgentGrant.create(agentId, request.permission(), request.resourceType(),
                request.resourceId(), ownerUserId, now, request.expiresAt());
        AgentGrant saved = grants.saveAndFlush(grant);
        audit(saved, ownerUserId, "GRANT_CREATED", correlationId, now);
        return map(saved);
    }

    @Transactional
    public List<AgentGrantResponse> list(UUID ownerUserId, UUID agentId) {
        ownedAgent(ownerUserId, agentId);
        expireElapsed(clock.instant());
        return grants.findAllByAgentIdOrderByCreatedAtDesc(agentId).stream().map(this::map).toList();
    }

    @Transactional
    public AgentGrantResponse get(UUID ownerUserId, UUID agentId, UUID grantId) {
        ownedAgent(ownerUserId, agentId);
        expireElapsed(clock.instant());
        return map(grants.findByIdAndAgentId(grantId, agentId).orElseThrow(AgentNotFoundException::new));
    }

    @Transactional
    public void revoke(UUID ownerUserId, UUID agentId, UUID grantId, String correlationId) {
        ownedAgentForUpdate(ownerUserId, agentId);
        AgentGrant grant = grants.findByIdAndAgentId(grantId, agentId).orElseThrow(AgentNotFoundException::new);
        Instant now = clock.instant();
        grant.revoke(now, "OWNER_REQUEST");
        audit(grant, ownerUserId, "GRANT_REVOKED", correlationId, now);
    }

    private void validateRequest(UUID ownerUserId, AgentGrantRequest request, Instant now) {
        if (request == null || request.permission() == null || request.resourceType() == null
                || request.resourceId() == null) {
            throw invalid("INVALID_GRANT_REQUEST", "Permission and explicit resource scope are required");
        }
        if (!AgentGrantCompatibility.supports(request.permission(), request.resourceType())) {
            throw invalid("INCOMPATIBLE_PERMISSION", "Permission is not compatible with the resource type");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw invalid("INVALID_EXPIRATION", "expiresAt must be after the current time");
        }
        if (request.resourceType() == AgentResourceType.BROKER_ACCOUNT
                && brokerAccounts.findByIdAndOwnerId(request.resourceId(), ownerUserId).isEmpty()) {
            throw new AgentNotFoundException();
        }
        if (request.resourceType() == AgentResourceType.MARKET) {
            try {
                if (markets.findById(request.resourceId()) == null) {
                    throw new AgentNotFoundException();
                }
            } catch (RuntimeException failure) {
                if (failure instanceof AgentNotFoundException notFound) {
                    throw notFound;
                }
                throw invalid("RESOURCE_UNAVAILABLE", "The requested market could not be validated");
            }
        }
        if (request.resourceType() == AgentResourceType.RESEARCH_SCOPE) {
            if (researchScopes == null
                    || researchScopes.findByIdAndOwnerUserId(request.resourceId(), ownerUserId).isEmpty()) {
                throw new AgentNotFoundException();
            }
        }
    }

    private void expireElapsed(Instant now) {
        grants.expireElapsed(now);
    }

    private com.hope.trading.trading_core.agent.model.AgentIdentity ownedAgent(UUID ownerUserId, UUID agentId) {
        if (ownerUserId == null || agentId == null) {
            throw new AgentNotFoundException();
        }
        return agents.findByIdAndOwnerUserId(agentId, ownerUserId).orElseThrow(AgentNotFoundException::new);
    }

    private com.hope.trading.trading_core.agent.model.AgentIdentity ownedAgentForUpdate(UUID ownerUserId, UUID agentId) {
        if (ownerUserId == null || agentId == null) {
            throw new AgentNotFoundException();
        }
        return agents.findForUpdateByIdAndOwnerUserId(agentId, ownerUserId)
                .orElseThrow(AgentNotFoundException::new);
    }

    private AgentGrantException invalid(String code, String message) {
        return new AgentGrantException(HttpStatus.BAD_REQUEST.value(), code, message);
    }

    private AgentGrantException conflict(String code, String message) {
        return new AgentGrantException(HttpStatus.CONFLICT.value(), code, message);
    }

    private void audit(AgentGrant grant, UUID ownerUserId, String operation, String correlationId, Instant now) {
        String id = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.strip();
        if (id.length() > 128) {
            throw invalid("INVALID_CORRELATION_ID", "Correlation ID is too long");
        }
        auditEvents.save(AgentAuthorizationAuditEventEntity.grantChange(grant.agentId(), ownerUserId, grant.id(),
                grant.permission(), grant.resourceType(), grant.resourceId(), operation, id, now));
    }

    private AgentGrantResponse map(AgentGrant grant) {
        return new AgentGrantResponse(grant.id(), grant.agentId(), grant.permission(), grant.resourceType(),
                grant.resourceId(), grant.status(), grant.createdAt(), grant.expiresAt(), grant.revokedAt(),
                grant.revocationReason(), grant.grantVersion());
    }
}
