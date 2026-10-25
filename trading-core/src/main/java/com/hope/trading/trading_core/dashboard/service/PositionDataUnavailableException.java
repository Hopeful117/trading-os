package com.hope.trading.trading_core.dashboard.service;

public class PositionDataUnavailableException extends RuntimeException {
    public PositionDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
