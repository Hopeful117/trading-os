package com.hope.trading.trading_core.execution.application.port;

import java.util.UUID;

@FunctionalInterface
public interface T1ApprovalVerifier {
    boolean isApproved(UUID evaluationId, UUID executionIntentId);
}
