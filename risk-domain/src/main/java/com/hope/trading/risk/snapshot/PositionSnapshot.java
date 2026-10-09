package com.hope.trading.risk.snapshot;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.RiskTypes.ProtectionStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PositionSnapshot(
        UUID positionId, String instrument, BigDecimal signedQuantity,
        Money marketValue, Optional<Money> lossAtStop, Money marginUsed, ProtectionStatus protectionStatus
) {
    public PositionSnapshot(UUID positionId, String instrument, BigDecimal signedQuantity,
                            Money marketValue, Money lossAtStop, Money marginUsed) {
        this(positionId, instrument, signedQuantity, marketValue, Optional.ofNullable(lossAtStop), marginUsed,
                ProtectionStatus.PROTECTED);
    }
    public PositionSnapshot(UUID positionId, String instrument, BigDecimal signedQuantity,
                            Money marketValue, Money lossAtStop, Money marginUsed,
                            ProtectionStatus protectionStatus) {
        this(positionId, instrument, signedQuantity, marketValue, Optional.ofNullable(lossAtStop),
                marginUsed, protectionStatus);
    }
    public PositionSnapshot {
        Objects.requireNonNull(positionId);
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        Objects.requireNonNull(signedQuantity); Objects.requireNonNull(marketValue);
        Objects.requireNonNull(lossAtStop); Objects.requireNonNull(marginUsed);
        Objects.requireNonNull(protectionStatus);
        if (instrument.isEmpty() || signedQuantity.signum() == 0) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (marketValue.amount().signum() < 0 || lossAtStop.map(Money::amount).orElse(java.math.BigDecimal.ZERO).signum() < 0
                || marginUsed.amount().signum() < 0) throw new IllegalArgumentException("Negative position value");
    }
}
