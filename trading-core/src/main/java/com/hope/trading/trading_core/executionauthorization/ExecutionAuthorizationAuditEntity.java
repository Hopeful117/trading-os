package com.hope.trading.trading_core.executionauthorization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_authorization_audit")
public class ExecutionAuthorizationAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) private UUID id;
    @Column(name = "authorization_id", nullable = false, updatable = false) private UUID authorizationId;
    @Column(name = "actor_user_id", updatable = false) private UUID actorUserId;
    @Column(name = "actor_agent_id", updatable = false) private UUID actorAgentId;
    @Column(nullable = false, length = 40, updatable = false) private String event;
    @Column(nullable = false, length = 32, updatable = false) private String outcome;
    @Column(name = "correlation_id", length = 128, updatable = false) private String correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    protected ExecutionAuthorizationAuditEntity() { }
    public static ExecutionAuthorizationAuditEntity of(UUID authorizationId, UUID actorUserId,
            UUID actorAgentId, String event, String outcome, String correlationId, Instant occurredAt) {
        var value = new ExecutionAuthorizationAuditEntity();
        value.authorizationId = authorizationId;
        value.actorUserId = actorUserId;
        value.actorAgentId = actorAgentId;
        value.event = event;
        value.outcome = outcome;
        value.correlationId = correlationId;
        value.occurredAt = occurredAt;
        return value;
    }
}
