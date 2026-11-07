package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventEntity;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.repository.AgentGrantRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentAuthorizationService {
    private final AgentIdentityRepository agents;
    private final AgentGrantRepository grants;
    private final BrokerAccountRepository brokerAccounts;
    private final MarketDataClient markets;
    private final AgentAuthorizationAuditEventRepository auditEvents;
    private final Clock clock;

    @Transactional
    public Decision authorize(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                              UUID resourceId, String correlationId) {
        Instant now = clock.instant();
        String reason = validateInput(agentId, permission, resourceType, resourceId);
        if (reason == null) {
            var agent = agents.findById(agentId).orElse(null);
            if (agent == null) {
                reason = "UNKNOWN_AGENT";
            } else if (agent.status() != AgentStatus.ACTIVE) {
                reason = "INACTIVE_AGENT";
            } else if (!resourceBelongsToOwner(resourceType, resourceId, agent.ownerUserId())) {
                reason = "RESOURCE_NOT_OWNED_OR_UNKNOWN";
            } else {
                AgentGrant grant = grants.findEffective(agentId, permission, resourceType, resourceId, now)
                        .orElse(null);
                if (grant == null) {
                    reason = "MISSING_OR_INEFFECTIVE_GRANT";
                }
            }
        }
        boolean allowed = reason == null;
        recordAudit(agentId, permission, resourceType, resourceId, allowed ? "ALLOWED" : "DENIED",
                allowed ? null : reason, correlationId, now);
        return new Decision(allowed, reason);
    }

    private String validateInput(UUID agentId, AgentPermission permission, AgentResourceType resourceType,
                                 UUID resourceId) {
        if (agentId == null) return "UNKNOWN_AGENT";
        if (permission == null || resourceType == null || resourceId == null) return "INVALID_SCOPE";
        if (!AgentGrantCompatibility.supports(permission, resourceType)) return "INCOMPATIBLE_PERMISSION";
        if (resourceType == AgentResourceType.RESEARCH_SCOPE) return "UNSUPPORTED_RESOURCE_SCOPE";
        return null;
    }

    private boolean resourceBelongsToOwner(AgentResourceType resourceType, UUID resourceId, UUID ownerId) {
        try {
            return switch (resourceType) {
                case BROKER_ACCOUNT -> brokerAccounts.findByIdAndOwnerId(resourceId, ownerId).isPresent();
                case MARKET -> markets.findById(resourceId) != null;
                case RESEARCH_SCOPE -> false;
            };
        } catch (RuntimeException failure) {
            return false;
        }
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

    public record Decision(boolean allowed, String denialReason) {
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
