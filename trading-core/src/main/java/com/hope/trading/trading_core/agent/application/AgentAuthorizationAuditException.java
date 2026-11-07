package com.hope.trading.trading_core.agent.application;

public class AgentAuthorizationAuditException extends RuntimeException {
    public AgentAuthorizationAuditException(Throwable cause) {
        super("Authorization audit persistence failed", cause);
    }
}
