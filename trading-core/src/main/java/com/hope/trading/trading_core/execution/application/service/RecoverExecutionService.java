package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.pipeline.recovery.*;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import java.time.Clock;
import java.util.*;

public final class RecoverExecutionService {
    private final RecoverableExecutionDiscoveryStep discovery;
    private final ExecutionInspectionStep inspection;
    private final RecoveryStrategyStep strategy;
    private final BrokerReconciliationStep reconciliation;
    private final RecoveryFinalizationStep finalization;
    private final ExecutionEventPublisher events;
    private final ExecutionMetrics metrics;
    private final Clock clock;
    public RecoverExecutionService(RecoverableExecutionDiscoveryStep discovery,
            ExecutionInspectionStep inspection, RecoveryStrategyStep strategy,
            BrokerReconciliationStep reconciliation, RecoveryFinalizationStep finalization,
            ExecutionEventPublisher events, ExecutionMetrics metrics, Clock clock) {
        this.discovery = Objects.requireNonNull(discovery); this.inspection = Objects.requireNonNull(inspection);
        this.strategy = Objects.requireNonNull(strategy); this.reconciliation = Objects.requireNonNull(reconciliation);
        this.finalization = Objects.requireNonNull(finalization); this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics); this.clock = Objects.requireNonNull(clock);
    }
    public List<ExecutionIntent> recoverAll() {
        List<ExecutionIntent> recovered = new ArrayList<>();
        for (ExecutionIntent intent : discovery.execute()) {
            intent.addEvent(new ExecutionEvent.ExecutionRecoveryStarted(
                    intent.id(), clock.instant()));
            metrics.recoveryStarted();
            RecoveryPipelineContext context = new RecoveryPipelineContext(intent, clock.instant());
            inspection.execute(context); strategy.execute(context);
            reconciliation.execute(context); finalization.execute(context);
            events.publish(intent.pullEvents()); recovered.add(intent);
        }
        return List.copyOf(recovered);
    }
}
