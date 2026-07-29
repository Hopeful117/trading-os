package com.hope.trading.broker_service.credential.domain;

public record SafeProviderDiagnostics(String code, String safeMessage) {
    public SafeProviderDiagnostics {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (safeMessage == null || safeMessage.isBlank()) throw new IllegalArgumentException("safeMessage is required");
    }
}
