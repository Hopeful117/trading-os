package com.hope.trading.broker_service.broker.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface HistoricalFeeScheduleAcquirer {
    FeeScheduleObservation acquire(UUID brokerAccountId, String instrument);

    record FeeScheduleObservation(
            String instrument,
            String productType,
            BigDecimal makerRate,
            BigDecimal takerRate,
            String settlementAsset,
            String sourceId
    ) { }
}
