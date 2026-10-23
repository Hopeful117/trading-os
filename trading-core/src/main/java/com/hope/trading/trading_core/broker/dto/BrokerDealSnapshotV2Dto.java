package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerDealSnapshotV2Dto(String providerTradeReference, String instrument,
                                      String settlementAsset, String side,
                                      BigDecimal quantity, BigDecimal price,
                                      BigDecimal fee, BigDecimal swap, BigDecimal realizedPnl,
                                      Instant closedAt) {
}
