package com.hope.trading.trading_core.execution.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ExecutionIntentId(UUID value) {
    public ExecutionIntentId { Objects.requireNonNull(value); }
    public static ExecutionIntentId newId() { return new ExecutionIntentId(UUID.randomUUID()); }
}
