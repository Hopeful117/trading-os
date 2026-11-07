package com.hope.trading.trading_core.agent.application;

public class AgentNotFoundException extends RuntimeException {
    public AgentNotFoundException() {
        super("Agent was not found");
    }
}
