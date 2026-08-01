package com.hope.trading.trading_core.risk.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BrokerRiskFactsPort {
    Snapshot load(UUID brokerAccountId, Instant from, Instant to);

    record Snapshot(UUID brokerAccountId, long sourceVersion, Instant observedAt, boolean complete,
                    List<String> unavailabilityReasons, Map<String, BigDecimal> assetBalances,
                    Account account, List<Position> positions, List<ClosedTrade> closedTrades,
                    List<LedgerEntry> ledgerEntries, String sourcePayload) { }
    record Account(String valuationAsset, BigDecimal balance, BigDecimal equity, BigDecimal margin) { }
    record Position(UUID positionId, String providerPositionReference, String providerReferenceProvenance,
                    String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                    BigDecimal marketValue, BigDecimal margin, BigDecimal protectedQuantity,
                    List<Stop> protectiveStops) { }
    record Stop(String providerOrderReference, String providerReferenceProvenance,
                BigDecimal quantity, BigDecimal stopPrice) { }
    record ClosedTrade(String providerTradeReference, String instrument, String settlementAsset, BigDecimal fee,
                       BigDecimal realizedPnl, Instant closedAt) { }
    record LedgerEntry(String providerLedgerReference, String asset, String type,
                       BigDecimal amount, BigDecimal fee, BigDecimal balance, Instant occurredAt) { }
}
