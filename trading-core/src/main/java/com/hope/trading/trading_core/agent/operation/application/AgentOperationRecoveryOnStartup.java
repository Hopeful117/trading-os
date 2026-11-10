package com.hope.trading.trading_core.agent.operation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AgentOperationRecoveryOnStartup {
    private final AgentOperationService operations;

    @Value("${trading-core.agent-operation.recovery-age-seconds:60}")
    private long recoveryAgeSeconds;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleOperations() {
        operations.recoverStaleExecuting(Duration.ofSeconds(Math.max(1, recoveryAgeSeconds)));
    }
}
