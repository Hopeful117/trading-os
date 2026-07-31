package com.hope.trading.trading_core.execution.application.port;

import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import java.util.List;

@FunctionalInterface
public interface ExecutionEventPublisher {
    void publish(List<ExecutionEvent> events);
}
