package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.pipeline.recovery.*;
import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.time.Clock;
import java.util.*;

public final class RecoverExecutionService {
    private static final Set<ExecutionStatus> RECOVERABLE = Set.of(
            ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN,
            ExecutionStatus.RECONCILIATION_IN_PROGRESS,
            ExecutionStatus.RECOVERY_BLOCKED);
    private final RecoverableExecutionDiscoveryStep discovery;
    private final ExecutionInspectionStep inspection;
    private final RecoveryStrategyStep strategy;
    private final BrokerReconciliationStep reconciliation;
    private final RecoveryFinalizationStep finalization;
    private final ExecutionEventPublisher events;
    private final ExecutionMetrics metrics;
    private final Clock clock;
    private final ExecutionIntentRepositoryPort intents;
    public RecoverExecutionService(RecoverableExecutionDiscoveryStep discovery,
            ExecutionInspectionStep inspection, RecoveryStrategyStep strategy,
            BrokerReconciliationStep reconciliation, RecoveryFinalizationStep finalization,
            ExecutionEventPublisher events, ExecutionMetrics metrics, Clock clock,
            ExecutionIntentRepositoryPort intents) {
        this.discovery = Objects.requireNonNull(discovery); this.inspection = Objects.requireNonNull(inspection);
        this.strategy = Objects.requireNonNull(strategy); this.reconciliation = Objects.requireNonNull(reconciliation);
        this.finalization = Objects.requireNonNull(finalization); this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics); this.clock = Objects.requireNonNull(clock);
        this.intents = Objects.requireNonNull(intents);
    }
    public ExecutionIntent recoverOne(ExecutionIntentId id) {
        ExecutionIntent intent = intents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found"));
        if (!RECOVERABLE.contains(intent.status())) {
            throw new IllegalStateException("Execution cannot be reconciled in " + intent.status() + " state");
        }
        intent.addEvent(new ExecutionEvent.ExecutionRecoveryStarted(intent.id(), clock.instant()));
        metrics.recoveryStarted();
        RecoveryPipelineContext context = new RecoveryPipelineContext(intent, clock.instant());
        inspection.execute(context); strategy.execute(context);
        reconciliation.execute(context); finalization.execute(context);
        events.publish(intent.pullEvents());
        intents.save(intent);
        return intent;
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
