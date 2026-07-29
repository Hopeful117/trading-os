package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.*;
import java.util.concurrent.atomic.*;

public class ExecutionControl implements CancellationToken {
    private final AtomicReference<CancellationRequest> request = new AtomicReference<>();
    private final List<ExecutionEngineState> states =
            new java.util.concurrent.CopyOnWriteArrayList<>(List.of(ExecutionEngineState.RUNNING));
    public boolean requestCancellation(CancellationRequest cancellation) {
        boolean accepted = request.compareAndSet(null, cancellation);
        if (accepted) states.add(ExecutionEngineState.CANCEL_REQUESTED);
        return accepted;
    }
    @Override public boolean isCancellationRequested() { return request.get() != null; }
    public Optional<CancellationRequest> cancellationRequest() {
        return Optional.ofNullable(request.get());
    }
    void completeCancellation() { states.add(ExecutionEngineState.CANCELLED); }
    void completeNormally() { states.add(ExecutionEngineState.COMPLETED); }
    public List<ExecutionEngineState> stateHistory() { return List.copyOf(states); }
}
