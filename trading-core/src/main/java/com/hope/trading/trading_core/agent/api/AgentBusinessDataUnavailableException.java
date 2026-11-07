package com.hope.trading.trading_core.agent.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AgentBusinessDataUnavailableException extends RuntimeException {
    public AgentBusinessDataUnavailableException() {
        super("Authorized broker data is temporarily unavailable");
    }
}
