package com.hope.trading.trading_core.execution.domain.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.DuplicateExecutionException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import java.util.Objects;

public final class IdempotencyService {
    public void ensureUnique(IdempotencyKey key, ExecutionIntentRepositoryPort repository) {
        if (repository.findByIdempotencyKey(Objects.requireNonNull(key)).isPresent()) {
            throw new DuplicateExecutionException();
        }
    }
    public void verifyIdentity(ExecutionIntent intent, IdempotencyKey expected) {
        if (!intent.idempotencyKey().equals(expected)) throw new DuplicateExecutionException();
        if (intent.activeAttemptId().isPresent()) throw new DuplicateExecutionException();
    }
}
