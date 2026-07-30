package com.hope.trading.risk.snapshot;

import com.hope.trading.risk.domain.Money;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PositionSnapshot(
        UUID positionId, String instrument, BigDecimal signedQuantity,
        Money marketValue, Money lossAtStop, Money marginUsed
) {
    public PositionSnapshot {
        Objects.requireNonNull(positionId);
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        Objects.requireNonNull(signedQuantity); Objects.requireNonNull(marketValue);
        Objects.requireNonNull(lossAtStop); Objects.requireNonNull(marginUsed);
        if (instrument.isEmpty() || signedQuantity.signum() == 0) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (marketValue.amount().signum() < 0 || lossAtStop.amount().signum() < 0
                || marginUsed.amount().signum() < 0) throw new IllegalArgumentException("Negative position value");
    }
}
