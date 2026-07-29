package com.hope.trading.market_intelligence.domain.artifact;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ArtifactFreshness(
        Instant producedAt,
        Instant validUntil,
        Instant invalidatedAt,
        String sourceVersion
) {
    public ArtifactFreshness {
        sourceVersion = sourceVersion == null ? null : sourceVersion.trim();
        if (producedAt != null && validUntil != null && validUntil.isBefore(producedAt)) {
            throw new IllegalArgumentException("validUntil cannot precede producedAt");
        }
    }

    public static ArtifactFreshness validUntil(
            Instant producedAt,
            Instant validUntil,
            String sourceVersion
    ) {
        return new ArtifactFreshness(
                Objects.requireNonNull(producedAt),
                Objects.requireNonNull(validUntil),
                null,
                sourceVersion
        );
    }

    public ArtifactFreshness invalidate(Instant at) {
        return new ArtifactFreshness(producedAt, validUntil, at, sourceVersion);
    }

    public Optional<Instant> invalidationTime() {
        return Optional.ofNullable(invalidatedAt);
    }
}
