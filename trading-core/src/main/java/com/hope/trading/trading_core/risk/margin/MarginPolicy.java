package com.hope.trading.trading_core.risk.margin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, provider-neutral collateral policy for a PAPER account. */
public record MarginPolicy(UUID id, String version, String instrument, BigDecimal marginRate,
                           String sourceCategory, String sourceReference, Instant effectiveAt,
                           Instant expiresAt, Instant configuredAt, int reportingCurrencyScale, boolean revoked,
                           boolean tiered, boolean dynamic, boolean hedging, boolean portfolioOffsets) {
    public static final int MAX_RATE_SCALE = 8;

    public MarginPolicy {
        if (id == null || blank(version) || blank(instrument) || marginRate == null
                || !marginRate.abs().equals(marginRate) || marginRate.signum() <= 0
                || marginRate.compareTo(BigDecimal.ONE) > 0 || marginRate.scale() > MAX_RATE_SCALE
                || blank(sourceCategory) || blank(sourceReference) || effectiveAt == null
                || configuredAt == null || (expiresAt != null && !expiresAt.isAfter(effectiveAt))
                || reportingCurrencyScale < 0 || reportingCurrencyScale > 18
                || tiered || dynamic || hedging || portfolioOffsets) {
            throw new IllegalArgumentException("Invalid V1 PAPER margin policy");
        }
        version = version.strip();
        instrument = instrument.strip();
        sourceCategory = sourceCategory.strip();
        sourceReference = sourceReference.strip();
    }

    public MarginPolicy(UUID id, String version, String instrument, BigDecimal marginRate,
                        String sourceCategory, String sourceReference, Instant effectiveAt,
                        Instant configuredAt, int reportingCurrencyScale, boolean revoked,
                        boolean tiered, boolean dynamic, boolean hedging, boolean portfolioOffsets) {
        this(id, version, instrument, marginRate, sourceCategory, sourceReference, effectiveAt, null,
                configuredAt, reportingCurrencyScale, revoked, tiered, dynamic, hedging, portfolioOffsets);
    }

    public boolean applicableAt(Instant at) {
        return at != null && !revoked && !effectiveAt.isAfter(at)
                && (expiresAt == null || at.isBefore(expiresAt));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
