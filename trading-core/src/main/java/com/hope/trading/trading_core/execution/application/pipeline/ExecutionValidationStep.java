package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.domain.service.*;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.util.Objects;

public final class ExecutionValidationStep {
    private final ExecutionValidationService validation;
    private final ExecutionLifecycleService lifecycle;
    public ExecutionValidationStep(ExecutionValidationService validation,
                                   ExecutionLifecycleService lifecycle) {
        this.validation = Objects.requireNonNull(validation);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }
    public void execute(ExecutionPipelineContext context) {
        validation.validateForExecution(context.intent(), context.now());
        if (context.intent().status() == ExecutionStatus.CREATED) {
            lifecycle.validate(context.intent(), context.now());
        }
    }
}
