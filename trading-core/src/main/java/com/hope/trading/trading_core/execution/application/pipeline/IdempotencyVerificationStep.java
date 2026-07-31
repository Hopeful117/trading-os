package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.domain.service.IdempotencyService;
import java.util.Objects;

public final class IdempotencyVerificationStep {
    private final IdempotencyService idempotency;
    public IdempotencyVerificationStep(IdempotencyService idempotency) {
        this.idempotency = Objects.requireNonNull(idempotency);
    }
    public void execute(ExecutionPipelineContext context) {
        idempotency.verifyIdentity(context.intent(), context.intent().idempotencyKey());
    }
}
