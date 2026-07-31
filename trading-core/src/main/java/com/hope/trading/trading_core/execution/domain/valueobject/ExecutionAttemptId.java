package com.hope.trading.trading_core.execution.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ExecutionAttemptId(UUID value) {
    public ExecutionAttemptId { Objects.requireNonNull(value); }
    public static ExecutionAttemptId newId() { return new ExecutionAttemptId(UUID.randomUUID()); }
}
