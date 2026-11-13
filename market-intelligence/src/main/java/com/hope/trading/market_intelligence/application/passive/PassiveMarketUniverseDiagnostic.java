package com.hope.trading.market_intelligence.application.passive;

import java.util.UUID;

public record PassiveMarketUniverseDiagnostic(
        Code code,
        Severity severity,
        UUID marketId,
        String provider,
        String productType,
        String message
) {
    public enum Code {
        DISABLED_ENTRY,
        UNKNOWN_MARKET,
        PROVIDER_MISMATCH,
        PRODUCT_TYPE_MISMATCH,
        CATALOGUE_IDENTITY_CONFLICT,
        CATALOGUE_UNAVAILABLE
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
