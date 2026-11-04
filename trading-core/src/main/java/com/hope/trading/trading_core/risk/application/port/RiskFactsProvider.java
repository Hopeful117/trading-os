package com.hope.trading.trading_core.risk.application.port;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.hope.trading.risk.domain.RiskTypes.ProtectionStatus;

/** Mode-aware application boundary for the facts consumed by risk evaluation. */
public interface RiskFactsProvider {
    Snapshot load(com.hope.trading.trading_core.model.Account account, BrokerAccount brokerAccount,
                  UUID sourceId, Instant from, Instant to);

    default Snapshot load(com.hope.trading.trading_core.model.Account account, BrokerAccount brokerAccount,
                          UUID sourceId, Instant from, Instant to, RiskFactRequirements requirements) {
        return load(account, brokerAccount, sourceId, from, to);
    }

    record Snapshot(UUID sourceId, long sourceVersion, Instant observedAt, boolean complete,
                    List<String> unavailabilityReasons, Map<String, BigDecimal> assetBalances,
                    Account account, List<Position> positions, List<ClosedTrade> closedTrades,
                    List<LedgerEntry> ledgerEntries, String sourcePayload,
                    DealAvailability dealAvailability) {
        public Snapshot(UUID sourceId, long sourceVersion, Instant observedAt, boolean complete,
                        List<String> unavailabilityReasons, Map<String, BigDecimal> assetBalances,
                        Account account, List<Position> positions, List<ClosedTrade> closedTrades,
                        List<LedgerEntry> ledgerEntries, String sourcePayload) {
            this(sourceId, sourceVersion, observedAt, complete, unavailabilityReasons, assetBalances,
                    account, positions, closedTrades, ledgerEntries, sourcePayload,
                    closedTrades == null || closedTrades.isEmpty()
                            ? DealAvailability.AVAILABLE_EMPTY : DealAvailability.AVAILABLE_NON_EMPTY);
        }
    }
    enum DealAvailability { AVAILABLE_EMPTY, AVAILABLE_NON_EMPTY, UNAVAILABLE_OR_ABSENT, TRUNCATED }
    record Account(String valuationAsset, BigDecimal balance, BigDecimal equity,
                   BigDecimal margin, BigDecimal startingBalance, EquityFact equityFact) {
        public Account(String valuationAsset, BigDecimal balance, BigDecimal equity,
                       BigDecimal margin, BigDecimal startingBalance) {
            this(valuationAsset, balance, equity, margin, startingBalance,
                    EquityFact.unavailable("EQUITY_PROVENANCE_UNAVAILABLE"));
        }
    }

    record EquityFact(BigDecimal value, String currency, RiskFactRequirements.EquityProvenance provenance,
                      String availability, Instant observedAt, Instant capturedAt,
                      boolean fresh, boolean complete, boolean coherent, String source,
                      String resolutionMethod, List<String> reasons) {
        public EquityFact {
            provenance = java.util.Objects.requireNonNull(provenance, "equity provenance is required");
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }

        public static EquityFact unavailable(String reason) {
            return new EquityFact(null, null, RiskFactRequirements.EquityProvenance.UNAVAILABLE,
                    "UNAVAILABLE", null, null, false, false, false, "UNKNOWN", "UNAVAILABLE",
                    reason == null || reason.isBlank() ? List.of("EQUITY_UNAVAILABLE") : List.of(reason));
        }

        public static EquityFact available(BigDecimal value, String currency, Instant observedAt,
                                           Instant capturedAt, String source, String resolutionMethod) {
            if (value == null || currency == null || currency.isBlank() || observedAt == null
                    || capturedAt == null || source == null || source.isBlank()
                    || resolutionMethod == null || resolutionMethod.isBlank()) {
                throw new IllegalArgumentException("available equity facts are incomplete");
            }
            return new EquityFact(value, currency,
                    RiskFactRequirements.EquityProvenance.BROKER_DERIVED, "AVAILABLE",
                    observedAt, capturedAt, true, true, true, source, resolutionMethod, List.of());
        }
    }
    record Position(UUID positionId, String sourcePositionReference, String sourceReferenceProvenance,
                    String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                    BigDecimal marketValue, BigDecimal margin, BigDecimal protectedQuantity,
                     List<Stop> protectiveStops, ProtectionStatus protectionStatus) {
        public Position(UUID positionId, String sourcePositionReference, String sourceReferenceProvenance,
                        String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                        BigDecimal marketValue, BigDecimal margin, BigDecimal protectedQuantity,
                        List<Stop> protectiveStops) {
            this(positionId, sourcePositionReference, sourceReferenceProvenance, instrument, signedQuantity,
                    entryPrice, marketValue, margin, protectedQuantity, protectiveStops,
                    RiskFactsProvider.protectionStatus(protectedQuantity, signedQuantity, protectiveStops));
        }
    }
    record Stop(String sourceOrderReference, String sourceReferenceProvenance,
                BigDecimal quantity, BigDecimal stopPrice) { }
    record ClosedTrade(String sourceTradeReference, String instrument, String settlementAsset, BigDecimal fee,
                       BigDecimal realizedPnl, Instant closedAt) { }
    record LedgerEntry(String sourceLedgerReference, String asset, String type,
                       BigDecimal amount, BigDecimal fee, BigDecimal balance, Instant occurredAt) { }

    static Snapshot fromBroker(BrokerRiskFactsPort.Snapshot value) {
        var account = value.account() == null ? null : new Account(value.account().valuationAsset(),
                value.account().balance(), value.account().equity(), value.account().margin(),
                value.account().startingBalance(), value.account().equityFact());
        var positions = value.positions().stream().map(p -> {
            List<Stop> stops = p.protectiveStops() == null ? List.of() : p.protectiveStops().stream()
                    .map(s -> new Stop(s.providerOrderReference(), s.providerReferenceProvenance(),
                            s.quantity(), s.stopPrice())).toList();
            return new Position(p.positionId(), p.providerPositionReference(), p.providerReferenceProvenance(),
                    p.instrument(), p.signedQuantity(), p.entryPrice(), p.marketValue(), p.margin(),
                    p.protectedQuantity(), stops, protectionStatus(p.protectedQuantity(), p.signedQuantity(), stops));
        }).toList();
        var closed = value.closedTrades().stream().map(t -> new ClosedTrade(t.providerTradeReference(),
                t.instrument(), t.settlementAsset(), t.fee(), t.realizedPnl(), t.closedAt())).toList();
        var ledger = value.ledgerEntries().stream().map(e -> new LedgerEntry(e.providerLedgerReference(),
                e.asset(), e.type(), e.amount(), e.fee(), e.balance(), e.occurredAt())).toList();
        return new Snapshot(value.brokerAccountId(), value.sourceVersion(), value.observedAt(), value.complete(),
                value.unavailabilityReasons(), value.assetBalances(), account, positions, closed, ledger,
                value.sourcePayload());
    }

    static Snapshot fromBroker(BrokerRiskFactsPort.Snapshot value, RiskFactRequirements requirements) {
        Snapshot snapshot = fromBroker(value);
        if (requirements == null || !requirements.equity() || equityAdmissible(snapshot.account(), requirements)) {
            return snapshot;
        }
        List<String> reasons = new java.util.ArrayList<>(snapshot.unavailabilityReasons() == null
                ? List.of() : snapshot.unavailabilityReasons());
        reasons.add(snapshot.account() == null || snapshot.account().equity() == null
                ? "BROKER_EQUITY_MISSING" : "BROKER_EQUITY_PROVENANCE_OR_QUALITY_INVALID");
        return new Snapshot(snapshot.sourceId(), snapshot.sourceVersion(), snapshot.observedAt(), false,
                List.copyOf(new java.util.LinkedHashSet<>(reasons)), snapshot.assetBalances(), snapshot.account(),
                snapshot.positions(), snapshot.closedTrades(), snapshot.ledgerEntries(), snapshot.sourcePayload(),
                snapshot.dealAvailability());
    }

    static boolean equityAdmissible(Account account, RiskFactRequirements requirements) {
        return equityAdmissible(account, requirements, false);
    }

    static boolean equityAdmissible(Account account, RiskFactRequirements requirements, boolean paper) {
        if (requirements == null || !requirements.equity()) return true;
        if (account == null || account.equity() == null) return false;
        if (!requirements.equityContractRequired()) return true;
        EquityFact fact = account.equityFact();
        return fact != null && "AVAILABLE".equals(fact.availability())
                && fact.value() != null && fact.value().compareTo(account.equity()) == 0
                && fact.currency() != null && fact.currency().equalsIgnoreCase(account.valuationAsset())
                && fact.observedAt() != null && fact.capturedAt() != null
                && fact.fresh() && fact.complete() && fact.coherent()
                && (requirements.admissibleEquityProvenances().contains(fact.provenance())
                || (paper && fact.provenance() == RiskFactRequirements.EquityProvenance.BROKER_DERIVED
                && "PAPER_ACCOUNT_STATE".equals(fact.source())
                && "PAPER_MARK_TO_MARKET".equals(fact.resolutionMethod())));
    }

    static ProtectionStatus protectionStatus(BigDecimal protectedQuantity, BigDecimal quantity,
                                              List<?> protectiveStops) {
        if (protectedQuantity == null || quantity == null || protectiveStops == null) {
            return ProtectionStatus.UNKNOWN;
        }
        BigDecimal positionQuantity = quantity.abs();
        BigDecimal stopQuantity = protectiveStops.stream()
                .filter(java.util.Objects::nonNull)
                .map(stop -> stop instanceof Stop value ? value.quantity() : null)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Some broker adapters expose the correlated stop orders but do not populate
        // their aggregate protected quantity. The orders are the stronger evidence.
        if (!protectiveStops.isEmpty() && stopQuantity.compareTo(positionQuantity) == 0) {
            return ProtectionStatus.PROTECTED;
        }
        if (protectedQuantity.signum() == 0 && protectiveStops.isEmpty()) {
            return ProtectionStatus.UNPROTECTED;
        }
        if (protectedQuantity.compareTo(positionQuantity) == 0 && !protectiveStops.isEmpty()) {
            return ProtectionStatus.PROTECTED;
        }
        return ProtectionStatus.PARTIALLY_PROTECTED;
    }
}
