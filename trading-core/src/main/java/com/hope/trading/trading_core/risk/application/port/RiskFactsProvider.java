package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/** Mode-aware application boundary for the facts consumed by risk evaluation. */
public interface RiskFactsProvider {
    Snapshot load(com.hope.trading.trading_core.model.Account account, BrokerAccount brokerAccount,
                  UUID sourceId, Instant from, Instant to);

    record Snapshot(UUID sourceId, long sourceVersion, Instant observedAt, boolean complete,
                    List<String> unavailabilityReasons, Map<String, BigDecimal> assetBalances,
                    Account account, List<Position> positions, List<ClosedTrade> closedTrades,
                    List<LedgerEntry> ledgerEntries, String sourcePayload) { }
    record Account(String valuationAsset, BigDecimal balance, BigDecimal equity, BigDecimal margin) { }
    record Position(UUID positionId, String sourcePositionReference, String sourceReferenceProvenance,
                    String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                    BigDecimal marketValue, BigDecimal margin, BigDecimal protectedQuantity,
                    List<Stop> protectiveStops) { }
    record Stop(String sourceOrderReference, String sourceReferenceProvenance,
                BigDecimal quantity, BigDecimal stopPrice) { }
    record ClosedTrade(String sourceTradeReference, String instrument, String settlementAsset, BigDecimal fee,
                       BigDecimal realizedPnl, Instant closedAt) { }
    record LedgerEntry(String sourceLedgerReference, String asset, String type,
                       BigDecimal amount, BigDecimal fee, BigDecimal balance, Instant occurredAt) { }

    static Snapshot fromBroker(BrokerRiskFactsPort.Snapshot value) {
        var account = value.account() == null ? null : new Account(value.account().valuationAsset(),
                value.account().balance(), value.account().equity(), value.account().margin());
        var positions = value.positions().stream().map(p -> new Position(p.positionId(),
                p.providerPositionReference(), p.providerReferenceProvenance(), p.instrument(),
                p.signedQuantity(), p.entryPrice(), p.marketValue(), p.margin(), p.protectedQuantity(),
                p.protectiveStops().stream().map(s -> new Stop(s.providerOrderReference(),
                        s.providerReferenceProvenance(), s.quantity(), s.stopPrice())).toList())).toList();
        var closed = value.closedTrades().stream().map(t -> new ClosedTrade(t.providerTradeReference(),
                t.instrument(), t.settlementAsset(), t.fee(), t.realizedPnl(), t.closedAt())).toList();
        var ledger = value.ledgerEntries().stream().map(e -> new LedgerEntry(e.providerLedgerReference(),
                e.asset(), e.type(), e.amount(), e.fee(), e.balance(), e.occurredAt())).toList();
        return new Snapshot(value.brokerAccountId(), value.sourceVersion(), value.observedAt(), value.complete(),
                value.unavailabilityReasons(), value.assetBalances(), account, positions, closed, ledger,
                value.sourcePayload());
    }
}
