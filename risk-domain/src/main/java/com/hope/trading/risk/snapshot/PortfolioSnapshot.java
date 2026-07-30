package com.hope.trading.risk.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PortfolioSnapshot(UUID portfolioId, long version, Instant capturedAt,
                                List<PositionSnapshot> positions) {
    public PortfolioSnapshot {
        Objects.requireNonNull(portfolioId); Objects.requireNonNull(capturedAt);
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
        positions = List.copyOf(positions);
    }
}
