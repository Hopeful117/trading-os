package com.hope.trading.trading_core.opportunitydecision.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "opportunity_decisions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_opportunity_decision_idempotency",
                columnNames = {"actor_id", "idempotency_key"}),
        @UniqueConstraint(name = "uq_opportunity_decision_identity",
                columnNames = {"account_id", "opportunity_id", "opportunity_version", "decision"})
})
public class OpportunityDecisionEntity {
    @Id UUID id;
    @Column(name = "actor_id", nullable = false) UUID actorId;
    @Column(name = "account_id", nullable = false) UUID accountId;
    @Column(name = "opportunity_id", nullable = false) UUID opportunityId;
    @Column(name = "opportunity_version", nullable = false) long opportunityVersion;
    @Column(name = "thesis_id", nullable = false) UUID thesisId;
    @Column(name = "thesis_version", nullable = false) long thesisVersion;
    @Column(nullable = false, length = 32) String decision;
    @Column(length = 2000) String rationale;
    @Column(name = "idempotency_key", nullable = false, length = 200) String idempotencyKey;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected OpportunityDecisionEntity() { }

    public static OpportunityDecisionEntity from(com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision value) {
        var entity = new OpportunityDecisionEntity();
        entity.id = value.id();
        entity.actorId = value.actorId();
        entity.accountId = value.accountId();
        entity.opportunityId = value.opportunityId();
        entity.opportunityVersion = value.opportunityVersion();
        entity.thesisId = value.thesisId();
        entity.thesisVersion = value.thesisVersion();
        entity.decision = value.decision().name();
        entity.rationale = value.rationale();
        entity.idempotencyKey = value.idempotencyKey();
        entity.createdAt = value.createdAt();
        return entity;
    }

    public com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision toDomain() {
        return new com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision(
                id, actorId, accountId, opportunityId, opportunityVersion, thesisId, thesisVersion,
                com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision.Decision.valueOf(decision),
                rationale, idempotencyKey, createdAt);
    }
}
