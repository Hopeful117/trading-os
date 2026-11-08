package com.hope.trading.trading_core.agent.operation.application;
public class AgentOperationException extends RuntimeException {
    private final int status;
    public AgentOperationException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
