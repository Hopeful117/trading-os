package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import java.math.BigDecimal;
import java.util.Objects;

public record ProjectedPosition(
        String instrument, BigDecimal signedQuantity, Money exposure,
        Money lossAtStop, Money margin
) {
    public ProjectedPosition {
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        Objects.requireNonNull(signedQuantity); Objects.requireNonNull(exposure);
        Objects.requireNonNull(lossAtStop); Objects.requireNonNull(margin);
        if (instrument.isEmpty() || signedQuantity.signum() == 0) {
            throw new IllegalArgumentException("Projected position must be open");
        }
    }
}
