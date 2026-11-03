package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;

public record BrokerPositionPnlSnapshotV2Dto(String brokerPositionReference,
                                             BigDecimal grossUnrealizedPnl,
                                             BigDecimal netUnrealizedPnl,
                                             Integer monetaryScale) {
}
