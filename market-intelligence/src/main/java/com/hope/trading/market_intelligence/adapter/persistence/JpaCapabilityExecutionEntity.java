package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capability_executions")
class JpaCapabilityExecutionEntity {
    @Id @Column(name = "execution_id") UUID executionId;
    @Column(name = "analysis_execution_id", nullable = false) UUID analysisExecutionId;
    @Column(name = "execution_group_id", nullable = false) UUID executionGroupId;
    @Column(name = "capability_id", nullable = false, length = 120) String capabilityId;
    @Column(name = "capability_version", nullable = false, length = 40) String capabilityVersion;
    @Column(name = "state", nullable = false, length = 40) String state;
    @Column(name = "attempt_number", nullable = false) int attemptNumber;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
