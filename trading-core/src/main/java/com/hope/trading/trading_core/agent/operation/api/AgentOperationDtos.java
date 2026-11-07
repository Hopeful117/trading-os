package com.hope.trading.trading_core.agent.operation.api;

import com.hope.trading.trading_core.agent.operation.model.*;
import java.time.Instant;
import java.util.UUID;

public final class AgentOperationDtos {
    private AgentOperationDtos() {}
    public record CreateRequest(AgentOperationType operationType, String resourceType, UUID resourceId) {}
    public record DecisionRequest() {}
    public record Response(UUID id, UUID agentId, UUID ownerUserId, AgentOperationType operationType,
                           String resourceType, UUID resourceId, Instant createdAt, Instant expiresAt,
                           AgentOperationStatus status, String correlationId, Instant decidedAt,
                           UUID decidedByUserId, Instant executedAt, String outcome) {
        public static Response of(AgentOperationRequestEntity r) { return new Response(r.id(), r.agentId(), r.ownerUserId(), r.operationType(), r.resourceType(), r.resourceId(), r.createdAt(), r.expiresAt(), r.status(), r.correlationId(), r.decidedAt(), r.decidedByUserId(), r.executedAt(), r.outcome()); }
    }
}
