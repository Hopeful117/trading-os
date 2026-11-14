package com.hope.trading.market_intelligence.application.passive;

public record PassiveAwarenessQuery(
        String instrument,
        String provider,
        String productType,
        boolean currentOnly,
        int page,
        int size
) {
    public PassiveAwarenessQuery {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        instrument = normalize(instrument);
        provider = normalize(provider);
        productType = normalize(productType);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
