package com.hope.trading.trading_core.shared.domain.model;

import java.time.Instant;
import java.util.Objects;

public record FinancialContractProvenance(String sourceId, long sourceVersion, Instant observedAt) {
    public FinancialContractProvenance {
        sourceId = Objects.requireNonNull(sourceId, "source id is required").trim();
        if (sourceId.isEmpty()) throw new IllegalArgumentException("source id is required");
        if (sourceVersion < 1) throw new IllegalArgumentException("source version must be positive");
        Objects.requireNonNull(observedAt, "observation timestamp is required");
    }
}
