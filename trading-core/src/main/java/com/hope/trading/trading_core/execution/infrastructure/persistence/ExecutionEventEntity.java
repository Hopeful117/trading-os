package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="execution_event",indexes=@Index(name="idx_execution_event_intent",
        columnList="intent_id,occurred_at"))
public class ExecutionEventEntity {
    @Id public UUID id;
    @Column(name="intent_id",nullable=false,updatable=false) public UUID intentId;
    @Column(name="event_type",nullable=false,updatable=false) public String eventType;
    @Column(name="occurred_at",nullable=false,updatable=false) public Instant occurredAt;
    @Column(name="payload",nullable=false,updatable=false,length=2000) public String payload;
}
