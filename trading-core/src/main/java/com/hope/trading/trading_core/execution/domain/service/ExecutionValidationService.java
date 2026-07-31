package com.hope.trading.trading_core.execution.domain.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.*;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Instant;
import java.util.Objects;

public final class ExecutionValidationService {
    public void validateForExecution(ExecutionIntent intent, Instant now) {
        Objects.requireNonNull(intent); Objects.requireNonNull(now);
        if (!now.isBefore(intent.expiresAt())) throw new ExecutionExpiredException();
        if (intent.status().terminal()) {
            throw new InvalidExecutionStateException("Terminal execution cannot be submitted");
        }
        if (intent.status() != ExecutionStatus.CREATED
                && intent.status() != ExecutionStatus.VALIDATED) {
            throw new InvalidExecutionStateException(
                    "Execution cannot start from " + intent.status());
        }
        Objects.requireNonNull(intent.riskApproval(), "Risk approval is mandatory");
    }
}
