package com.hope.trading.broker_service.ctrader.authentication;

public class CTraderAuthenticationException extends RuntimeException {
    private final ProviderDiagnostic diagnostic;

    public CTraderAuthenticationException(String message) {
        this(message, null, null);
    }

    public CTraderAuthenticationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public CTraderAuthenticationException(String message, ProviderDiagnostic diagnostic) {
        this(message, null, diagnostic);
    }

    public CTraderAuthenticationException(String message, Throwable cause, ProviderDiagnostic diagnostic) {
        super(message, cause);
        this.diagnostic = diagnostic;
    }

    public ProviderDiagnostic diagnostic() {
        return diagnostic;
    }

    public record ProviderDiagnostic(
            String stage,
            Integer requestPayloadType,
            String requestMessage,
            Integer responsePayloadType,
            String responseMessage,
            String providerErrorCode,
            String providerReason,
            String providerDescription,
            String correlationId) {
    }
}
