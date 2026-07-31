package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_attempt", uniqueConstraints =
        @UniqueConstraint(name = "uk_execution_attempt_number",
                columnNames = {"intent_id", "attempt_number"}))
public class ExecutionAttemptEntity {
    @Id public UUID id;
    @Column(name="intent_id", nullable=false, updatable=false) public UUID intentId;
    @Column(name="attempt_number", nullable=false, updatable=false) public int attemptNumber;
    @Column(nullable=false) public String status;
    @Column(name="broker_correlation_id") public String brokerCorrelationId;
    @Column(name="result_code") public String resultCode;
    @Column(name="created_at", nullable=false, updatable=false) public Instant createdAt;
    @Column(name="started_at") public Instant startedAt;
    @Column(name="completed_at") public Instant completedAt;
    @Version public long version;
}
