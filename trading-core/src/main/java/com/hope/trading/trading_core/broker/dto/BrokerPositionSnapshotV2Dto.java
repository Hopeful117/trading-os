package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerPositionSnapshotV2Dto(
        String instrument,
        BigDecimal signedQuantity,
        BigDecimal entryPrice,
        Instant observedAt,
        String brokerPositionReference,
        BigDecimal margin,
        BigDecimal stopLoss) {
    public BrokerPositionSnapshotV2Dto(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                                       Instant observedAt, String brokerPositionReference) {
        this(instrument, signedQuantity, entryPrice, observedAt, brokerPositionReference, null, null);
    }
}
