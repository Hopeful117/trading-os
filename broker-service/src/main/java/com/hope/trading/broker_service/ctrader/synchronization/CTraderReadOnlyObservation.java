package com.hope.trading.broker_service.ctrader.synchronization;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Provider-owned facts kept inside the cTrader adapter boundary. */
public record CTraderReadOnlyObservation(
        String externalAccountId,
        String valuationAsset,
        BigDecimal balance,
        BigDecimal equity,
        BigDecimal usedMargin,
        BigDecimal freeMargin,
        List<Position> positions,
        List<Order> orders,
        List<Deal> deals,
        Instant startedAt,
        Instant providerObservedAt,
        Instant completedAt,
        boolean complete,
        List<String> reasons,
        AccountFacts accountFacts,
        CollectionState<Position> positionSet,
        CollectionState<Order> orderSet,
        CollectionState<PositionPnl> pnlSet,
        boolean pnlComplete) {

    public CTraderReadOnlyObservation(String externalAccountId, String valuationAsset,
                                      BigDecimal balance, BigDecimal equity, BigDecimal usedMargin,
                                      BigDecimal freeMargin, List<Position> positions, List<Order> orders,
                                      List<Deal> deals, Instant startedAt, Instant providerObservedAt,
                                      Instant completedAt, boolean complete, List<String> reasons) {
        this(externalAccountId, valuationAsset, balance, equity, usedMargin, freeMargin, positions, orders,
                deals, startedAt, providerObservedAt, completedAt, complete, reasons, AccountFacts.empty(),
                CollectionState.available(positions), CollectionState.available(orders),
                CollectionState.unavailable(), false);
    }

    public CTraderReadOnlyObservation {
        if (externalAccountId == null || externalAccountId.isBlank()) {
            throw new IllegalArgumentException("externalAccountId is required");
        }
        if (valuationAsset == null || valuationAsset.isBlank()) {
            throw new IllegalArgumentException("valuationAsset is required");
        }
        if (balance == null) throw new IllegalArgumentException("balance is required");
        positions = List.copyOf(positions);
        orders = List.copyOf(orders);
        deals = List.copyOf(deals);
        if (startedAt == null || providerObservedAt == null || completedAt == null) {
            throw new IllegalArgumentException("observation timestamps are required");
        }
        reasons = List.copyOf(reasons);
        accountFacts = Objects.requireNonNull(accountFacts, "accountFacts is required");
        positionSet = Objects.requireNonNull(positionSet, "positionSet is required");
        orderSet = Objects.requireNonNull(orderSet, "orderSet is required");
        pnlSet = Objects.requireNonNull(pnlSet, "pnlSet is required");
    }

    public record ProviderField<T>(boolean present, T value) {
        public ProviderField {
            if (!present && value != null) throw new IllegalArgumentException("absent provider field cannot have a value");
            if (present && value == null) throw new IllegalArgumentException("present provider field requires a value");
        }

        public static <T> ProviderField<T> absent() {
            return new ProviderField<>(false, null);
        }

        public static <T> ProviderField<T> present(T value) {
            return new ProviderField<>(true, Objects.requireNonNull(value, "provider value is required"));
        }
    }

    public enum CollectionAvailability {
        UNAVAILABLE_OR_ABSENT,
        AVAILABLE_EMPTY,
        AVAILABLE_NON_EMPTY
    }

    public record CollectionState<T>(CollectionAvailability availability, List<T> values) {
        public CollectionState {
            availability = Objects.requireNonNull(availability, "collection availability is required");
            values = List.copyOf(values);
            if (availability == CollectionAvailability.UNAVAILABLE_OR_ABSENT && !values.isEmpty()) {
                throw new IllegalArgumentException("unavailable collection cannot have values");
            }
            if (availability == CollectionAvailability.AVAILABLE_EMPTY && !values.isEmpty()) {
                throw new IllegalArgumentException("empty collection cannot have values");
            }
        }

        public static <T> CollectionState<T> unavailable() {
            return new CollectionState<>(CollectionAvailability.UNAVAILABLE_OR_ABSENT, List.of());
        }

        public static <T> CollectionState<T> available(List<T> values) {
            return new CollectionState<>(values.isEmpty()
                    ? CollectionAvailability.AVAILABLE_EMPTY : CollectionAvailability.AVAILABLE_NON_EMPTY, values);
        }
    }

    public record AccountFacts(
            ProviderField<String> accountType,
            ProviderField<String> totalMarginCalculationType,
            ProviderField<String> depositAssetId,
            ProviderField<Integer> moneyDigits,
            ProviderField<Long> leverageInCents,
            ProviderField<BigDecimal> managerBonus,
            ProviderField<BigDecimal> ibBonus,
            ProviderField<BigDecimal> nonWithdrawableBonus) {
        public AccountFacts {
            accountType = Objects.requireNonNull(accountType, "accountType is required");
            totalMarginCalculationType = Objects.requireNonNull(totalMarginCalculationType, "margin type is required");
            depositAssetId = Objects.requireNonNull(depositAssetId, "depositAssetId is required");
            moneyDigits = Objects.requireNonNull(moneyDigits, "moneyDigits is required");
            leverageInCents = Objects.requireNonNull(leverageInCents, "leverageInCents is required");
            managerBonus = Objects.requireNonNull(managerBonus, "managerBonus is required");
            ibBonus = Objects.requireNonNull(ibBonus, "ibBonus is required");
            nonWithdrawableBonus = Objects.requireNonNull(nonWithdrawableBonus, "nonWithdrawableBonus is required");
        }

        public static AccountFacts empty() {
            return new AccountFacts(ProviderField.absent(), ProviderField.absent(), ProviderField.absent(),
                    ProviderField.absent(), ProviderField.absent(), ProviderField.absent(),
                    ProviderField.absent(), ProviderField.absent());
        }
    }

    public record Position(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                           Instant observedAt, String providerReference, BigDecimal margin,
                           BigDecimal stopLoss, String providerSymbolId, String providerTradeSide,
                           BigDecimal providerVolume, ProviderField<Integer> moneyDigits,
                           ProviderField<BigDecimal> swap, ProviderField<BigDecimal> commission,
                           ProviderField<BigDecimal> mirroringCommission) {
        public Position(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                        Instant observedAt, String providerReference) {
            this(instrument, signedQuantity, entryPrice, observedAt, providerReference, null, null,
                    null, null, null, ProviderField.absent(), ProviderField.absent(),
                    ProviderField.absent(), ProviderField.absent());
        }

        public Position(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                        Instant observedAt, String providerReference, BigDecimal margin,
                        BigDecimal stopLoss) {
            this(instrument, signedQuantity, entryPrice, observedAt, providerReference, margin, stopLoss,
                    null, null, null, ProviderField.absent(), ProviderField.absent(),
                    ProviderField.absent(), ProviderField.absent());
        }

        public Position {
            if (instrument == null || instrument.isBlank()) throw new IllegalArgumentException("instrument is required");
            if (signedQuantity == null || signedQuantity.signum() == 0) throw new IllegalArgumentException("signedQuantity must be non-zero");
            if (entryPrice == null) throw new IllegalArgumentException("entryPrice is required");
            if (observedAt == null || providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("position identity is required");
            if (margin != null && margin.signum() < 0) throw new IllegalArgumentException("margin cannot be negative");
            if (stopLoss != null && stopLoss.signum() <= 0) throw new IllegalArgumentException("stopLoss must be positive");
            moneyDigits = Objects.requireNonNull(moneyDigits, "moneyDigits is required");
            swap = Objects.requireNonNull(swap, "swap is required");
            commission = Objects.requireNonNull(commission, "commission is required");
            mirroringCommission = Objects.requireNonNull(mirroringCommission, "mirroringCommission is required");
        }
    }

    public record PositionPnl(String providerPositionReference,
                              ProviderField<BigDecimal> grossUnrealizedPnl,
                              ProviderField<BigDecimal> netUnrealizedPnl,
                              ProviderField<Integer> moneyDigits) {
        public PositionPnl {
            if (providerPositionReference == null || providerPositionReference.isBlank()) {
                throw new IllegalArgumentException("position PnL identity is required");
            }
            grossUnrealizedPnl = Objects.requireNonNull(grossUnrealizedPnl, "gross PnL is required");
            netUnrealizedPnl = Objects.requireNonNull(netUnrealizedPnl, "net PnL is required");
            moneyDigits = Objects.requireNonNull(moneyDigits, "moneyDigits is required");
        }
    }

    public record Order(String providerReference, String clientReference, String status,
                        BigDecimal quantity, BigDecimal filledQuantity, Instant observedAt) {
        public Order {
            if (providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("order identity is required");
            if (quantity == null || filledQuantity == null || observedAt == null) throw new IllegalArgumentException("order facts are required");
        }
    }

    public record Deal(String providerReference, String providerPositionReference, String instrument,
                       BigDecimal quantity, String side, BigDecimal price, BigDecimal fee,
                       BigDecimal swap, BigDecimal realizedPnl, Instant executedAt,
                       boolean closingPosition) {
        public Deal(String providerReference, String instrument, BigDecimal quantity,
                    String side, BigDecimal price, BigDecimal fee, BigDecimal swap,
                    BigDecimal realizedPnl, Instant executedAt) {
            this(providerReference, null, instrument, quantity, side, price, fee, swap,
                    realizedPnl, executedAt, realizedPnl != null || swap != null);
        }

        public Deal {
            if (providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("deal identity is required");
            if (instrument == null || instrument.isBlank() || quantity == null || price == null || executedAt == null) {
                throw new IllegalArgumentException("deal facts are required");
            }
        }
    }
}
