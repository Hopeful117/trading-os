package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.RiskTypes.ProtectionStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record ProjectedPosition(
        String instrument, BigDecimal signedQuantity, Money exposure,
        Optional<Money> lossAtStop, Money margin, ProtectionStatus protectionStatus
) {
    public ProjectedPosition(String instrument, BigDecimal signedQuantity, Money exposure,
                             Money lossAtStop, Money margin) {
        this(instrument, signedQuantity, exposure, Optional.ofNullable(lossAtStop), margin,
                ProtectionStatus.PROTECTED);
    }
    public ProjectedPosition(String instrument, BigDecimal signedQuantity, Money exposure,
                             Money lossAtStop, Money margin, ProtectionStatus protectionStatus) {
        this(instrument, signedQuantity, exposure, Optional.ofNullable(lossAtStop), margin, protectionStatus);
    }
    public ProjectedPosition {
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        Objects.requireNonNull(signedQuantity); Objects.requireNonNull(exposure);
        Objects.requireNonNull(lossAtStop); Objects.requireNonNull(margin);
        Objects.requireNonNull(protectionStatus);
        if (instrument.isEmpty() || signedQuantity.signum() == 0) {
            throw new IllegalArgumentException("Projected position must be open");
        }
    }
}
