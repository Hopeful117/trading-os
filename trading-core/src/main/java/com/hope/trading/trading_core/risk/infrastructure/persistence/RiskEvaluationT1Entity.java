package com.hope.trading.trading_core.risk.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "RiskEvaluationT1Entity")
@Table(name = "risk_evaluation_t1")
@Immutable
@IdClass(RiskPersistence.T1EvaluationKey.class)
public class RiskEvaluationT1Entity {
    @Id UUID id;
    @Column(name = "execution_intent_id", nullable = false) UUID executionIntentId;
    @Column(name = "t0_evaluation_id", nullable = false) UUID t0EvaluationId;
    @Column(name = "account_id", nullable = false) UUID accountId;
    @Column(name = "evaluated_at", nullable = false) Instant evaluatedAt;
    @Column(nullable = false, length = 32) String status;
    @Column(length = 32) String decision;
    @Column(name = "unavailable_reason_code", length = 64) String unavailableReasonCode;
    @Column(name = "result_schema_version") Integer resultSchemaVersion;
    @Column(name = "result_payload", columnDefinition = "text") String resultPayload;
    @Column(name = "response_schema_version", nullable = false) Integer responseSchemaVersion;
    @Column(name = "response_payload", nullable = false, columnDefinition = "text") String responsePayload;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public record T1EvaluationKey(UUID id) implements java.io.Serializable {}
}