package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerOrderSnapshotV2Dto(String externalOrderId, String clientOrderId,
                                       String status, BigDecimal quantity,
                                       BigDecimal filledQuantity, Instant observedAt) {
}
