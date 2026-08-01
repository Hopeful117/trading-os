package com.hope.trading.market_intelligence.adapter.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

final class JpaTradePlanId implements Serializable {
    UUID tradePlanId;
    long version;

    public JpaTradePlanId() {
    }

    JpaTradePlanId(UUID tradePlanId, long version) {
        this.tradePlanId = tradePlanId;
        this.version = version;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof JpaTradePlanId that
                && version == that.version && Objects.equals(tradePlanId, that.tradePlanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePlanId, version);
    }
}
