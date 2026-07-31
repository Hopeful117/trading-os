package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.domain.service.RecoveryStrategyService;
import java.util.Objects;

public final class RecoveryStrategyStep {
    private final RecoveryStrategyService strategies;
    public RecoveryStrategyStep(RecoveryStrategyService strategies) {
        this.strategies = Objects.requireNonNull(strategies);
    }
    public void execute(RecoveryPipelineContext context) {
        context.strategy(strategies.determine(context.intent()));
    }
}
