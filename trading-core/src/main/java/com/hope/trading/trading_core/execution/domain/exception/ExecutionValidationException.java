package com.hope.trading.trading_core.execution.domain.exception;

import java.util.Objects;

/**
 * Thrown when human validation of an authorized Trade Plan fails.
 *
 * <p>Carries a machine-readable code, a human-readable message, and an HTTP
 * status code for the REST layer.
 */
public final class ExecutionValidationException extends RuntimeException {
    private final String code;
    private final int status;

    public ExecutionValidationException(String code, String message, int status) {
        super(message);
        this.code = Objects.requireNonNull(code);
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
