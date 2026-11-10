package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventEntity;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentGrantRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.ResearchScopeRepository;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;

@Service
public class AgentAuthorizationService {
    private final AgentIdentityRepository agents;
    private final AgentGrantRepository grants;
    private final BrokerAccountRepository brokerAccounts;
    private final MarketDataClient markets;
    private final ResearchScopeRepository researchScopes;
    private final AgentCredentialRepository credentials;
    private final AgentAuthorizationAuditEventRepository auditEvents;
    private final Clock clock;

    public AgentAuthorizationService(AgentIdentityRepository agents, AgentGrantRepository grants,
                                     BrokerAccountRepository brokerAccounts, MarketDataClient markets,
                                     AgentAuthorizationAuditEventRepository auditEvents, Clock clock) {
        this(agents, grants, brokerAccounts, markets, null, null, auditEvents, clock);
    }

    public AgentAuthorizationService(AgentIdentityRepository agents, AgentGrantRepository grants,
                                     BrokerAccountRepository brokerAccounts, MarketDataClient markets,
                                     ResearchScopeRepository researchScopes,
                                     AgentAuthorizationAuditEventRepository auditEvents, Clock clock) {
        this(agents, grants, brokerAccounts, markets, researchScopes, null, auditEvents, clock);
    }

    @Autowired
    public AgentAuthorizationService(AgentIdentityRepository agents, AgentGrantRepository grants,
                                     BrokerAccountRepository brokerAccounts, MarketDataClient markets,
                                     ResearchScopeRepository researchScopes, AgentCredentialRepository credentials,
                                     AgentAuthorizationAuditEventRepository auditEvents, Clock clock) {
        this.agents = agents;
        this.grants = grants;
        this.brokerAccounts = brokerAccounts;
        this.markets = markets;
        this.researchScopes = researchScopes;
        this.credentials = credentials;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Transactional
    public Decision authorize(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                              UUID resourceId, String correlationId) {
        Instant now = clock.instant();
        String reason = validateInput(agentId, permission, resourceType, resourceId);
        AgentGrant grant = null;
        var agent = agents.findById(agentId).orElse(null);
        if (reason == null) {
            if (agent == null) {
                reason = "UNKNOWN_AGENT";
            } else if (agent.status() != AgentStatus.ACTIVE) {
                reason = "INACTIVE_AGENT";
            } else if (!resourceBelongsToOwner(resourceType, resourceId, agent.ownerUserId())) {
                reason = "RESOURCE_NOT_OWNED_OR_UNKNOWN";
            } else {
                grant = grants.findEffective(agentId, permission, resourceType, resourceId, now)
                        .orElse(null);
                if (grant == null) {
                    reason = "MISSING_OR_INEFFECTIVE_GRANT";
                }
            }
        }
        boolean allowed = reason == null;
        recordAudit(agentId, permission, resourceType, resourceId, allowed ? "ALLOWED" : "DENIED",
                allowed ? null : reason, correlationId, now);
        return new Decision(allowed, reason, grant == null ? null : grant.id(),
                grant == null ? null : grant.grantVersion(), agent == null ? null : agent.version());
    }

    @Transactional
    public Decision authorizeResearch(UUID agentId, UUID credentialId, Integer credentialVersion,
                                      UUID scopeId, Set<UUID> marketIds, String correlationId) {
        return authorizeResearch(agentId, credentialId, credentialVersion, AgentPermission.RESEARCH_RUN,
                scopeId, marketIds, null, null, null, 0, 0, 0, 0, correlationId);
    }

    @Transactional
    public Decision authorizeHistoricalResearch(UUID agentId, UUID credentialId, Integer credentialVersion,
                                                 AgentPermission permission, UUID scopeId, Set<UUID> marketIds,
                                                Instant startCutoff, Instant endCutoff, String datasetProfile,
                                                long observations, long exportRows, long exportBytes,
                                                long durationSeconds, String correlationId) {
        if (permission != AgentPermission.RESEARCH_INSPECT && permission != AgentPermission.RESEARCH_EXPORT) {
            return deny(agentId, permission, scopeId, correlationId, "INCOMPATIBLE_PERMISSION");
        }
        return authorizeResearch(agentId, credentialId, credentialVersion, permission, scopeId, marketIds,
                startCutoff, endCutoff, datasetProfile, observations, exportRows, exportBytes,
                durationSeconds, correlationId);
    }

    @Transactional
    public Decision authorizeHistoricalResearchForBrokerAccount(UUID agentId, UUID credentialId,
                                                                Integer credentialVersion, UUID brokerAccountId,
                                                                Set<UUID> marketIds, Instant startCutoff,
                                                                Instant endCutoff, String datasetProfile,
                                                                long observations, String correlationId) {
        Decision base = authorize(agentId, AgentPermission.RESEARCH_INSPECT,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, correlationId);
        if (!base.allowed()) return base;
        if (credentials == null || credentialId == null || credentialVersion == null
                || credentials.findByIdAndAgentId(credentialId, agentId)
                .filter(value -> value.credentialVersion() == credentialVersion)
                .filter(value -> value.isActiveAt(clock.instant())).isEmpty()) {
            return denyBrokerAccount(agentId, brokerAccountId, correlationId, "INVALID_AGENT_CREDENTIAL");
        }
        if (marketIds == null || marketIds.isEmpty() || marketIds.size() > 2
                || marketIds.stream().anyMatch(value -> markets.findById(value) == null)
                || startCutoff == null || endCutoff == null || !startCutoff.isBefore(endCutoff)
                || datasetProfile == null || datasetProfile.isBlank() || observations <= 0) {
            return denyBrokerAccount(agentId, brokerAccountId, correlationId, "RESEARCH_REQUEST_INVALID");
        }
        return new Decision(true, null, base.grantId(), base.grantVersion(), base.agentVersion(),
                base.ownerUserId(), credentialId, credentialVersion);
    }

    private Decision authorizeResearch(UUID agentId, UUID credentialId, Integer credentialVersion,
                                       AgentPermission permission, UUID scopeId, Set<UUID> marketIds,
                                       Instant startCutoff, Instant endCutoff, String datasetProfile,
                                       long observations, long exportRows, long exportBytes,
                                       long durationSeconds, String correlationId) {
        Decision base = authorize(agentId, permission, AgentResourceType.RESEARCH_SCOPE,
                scopeId, correlationId);
        if (!base.allowed()) return base;
        if (credentials == null || credentialId == null || credentialVersion == null
                || credentials.findByIdAndAgentId(credentialId, agentId)
                .filter(value -> value.credentialVersion() == credentialVersion)
                .filter(value -> value.isActiveAt(clock.instant())).isEmpty()) {
            return deny(agentId, permission, scopeId, correlationId, "INVALID_AGENT_CREDENTIAL");
        }
        var agent = agents.findById(agentId).orElse(null);
        var scope = researchScopes.findById(scopeId).orElse(null);
        var brokerAccount = scope != null && scope.brokerAccountId() != null
                ? brokerAccounts.findByIdAndOwnerId(scope.brokerAccountId(), agent == null ? null : agent.ownerUserId()).orElse(null)
                : null;
        if (agent == null || scope == null || !scope.ownerUserId().equals(agent.ownerUserId())
                || !scope.effectiveAt(clock.instant()) || marketIds == null
                || marketIds.isEmpty() || !scope.marketIds().containsAll(marketIds)
                || (scope.brokerAccountId() != null && brokerAccount == null)) {
            return deny(agentId, permission, scopeId, correlationId, "RESEARCH_MARKET_OUT_OF_SCOPE");
        }
        if (permission != AgentPermission.RESEARCH_RUN) {
            var constraints = scope.historicalConstraints().orElse(null);
            if (constraints == null || startCutoff == null || endCutoff == null
                    || !startCutoff.isBefore(endCutoff)
                    || !constraints.permits(startCutoff, endCutoff, datasetProfile,
                    observations, exportRows, exportBytes, durationSeconds)) {
                return deny(agentId, permission, scopeId, correlationId, "HISTORICAL_SCOPE_CONSTRAINT_VIOLATION");
            }
        }
        return new Decision(true, null, base.grantId(), base.grantVersion(), base.agentVersion(),
                agent.ownerUserId(), credentialId, credentialVersion);
    }

    private String validateInput(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                                 UUID resourceId) {
        if (agentId == null) return "UNKNOWN_AGENT";
        if (permission == null || resourceType == null || resourceId == null) return "INVALID_SCOPE";
        if (!AgentGrantCompatibility.supports(permission, resourceType)) return "INCOMPATIBLE_PERMISSION";
        if (resourceType == AgentResourceType.RESEARCH_SCOPE && researchScopes == null) {
            return "UNSUPPORTED_RESOURCE_SCOPE";
        }
        return null;
    }

    private boolean resourceBelongsToOwner(AgentResourceType resourceType, UUID resourceId, UUID ownerId) {
        try {
            return switch (resourceType) {
                case BROKER_ACCOUNT -> brokerAccounts.findByIdAndOwnerId(resourceId, ownerId).isPresent();
                case MARKET -> markets.findById(resourceId) != null;
                case RESEARCH_SCOPE -> researchScopes != null
                        && researchScopes.findByIdAndOwnerUserId(resourceId, ownerId).isPresent();
            };
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private Decision deny(UUID agentId, AgentPermission permission, UUID resourceTypeId, String correlationId, String reason) {
        Instant now = clock.instant();
        recordAudit(agentId, permission, AgentResourceType.RESEARCH_SCOPE, resourceTypeId,
                "DENIED", reason, correlationId, now);
        return new Decision(false, reason);
    }

    private Decision denyBrokerAccount(UUID agentId, UUID brokerAccountId, String correlationId, String reason) {
        Instant now = clock.instant();
        recordAudit(agentId, AgentPermission.RESEARCH_INSPECT, AgentResourceType.BROKER_ACCOUNT, brokerAccountId,
                "DENIED", reason, correlationId, now);
        return new Decision(false, reason);
    }

    private void recordAudit(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                             UUID resourceId, String decision, String reason, String correlationId, Instant now) {
        try {
            auditEvents.save(AgentAuthorizationAuditEventEntity.of(agentId, permission, resourceType, resourceId,
                    decision, reason, normalizeCorrelationId(correlationId), now));
        } catch (RuntimeException failure) {
            throw new AgentAuthorizationAuditException(failure);
        }
    }

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }
        String normalized = correlationId.strip();
        if (normalized.length() > 128) {
            throw new AgentAuthorizationAuditException(
                    new IllegalArgumentException("Correlation ID is too long"));
        }
        return normalized;
    }

    public record Decision(boolean allowed, String denialReason, UUID grantId, Long grantVersion, Long agentVersion,
                           UUID ownerUserId, UUID credentialId, Integer credentialVersion) {
        public Decision(boolean allowed, String denialReason) {
            this(allowed, denialReason, null, null, null, null, null, null);
        }
        public Decision(boolean allowed, String denialReason, UUID grantId, Long grantVersion, Long agentVersion) {
            this(allowed, denialReason, grantId, grantVersion, agentVersion, null, null, null);
        }
        public Decision {
            if (allowed && denialReason != null) {
                throw new IllegalArgumentException("Allowed decisions cannot contain a denial reason");
            }
            if (!allowed && (denialReason == null || denialReason.isBlank())) {
                throw new IllegalArgumentException("Denied decisions require a reason");
            }
        }
    }
}
