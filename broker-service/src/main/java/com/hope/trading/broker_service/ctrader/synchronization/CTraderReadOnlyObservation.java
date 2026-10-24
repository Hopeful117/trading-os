package com.hope.trading.broker_service.ctrader.synchronization;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        List<String> reasons) {

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
    }

    public record Position(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                           Instant observedAt, String providerReference, BigDecimal margin,
                           BigDecimal stopLoss) {
        public Position(String instrument, BigDecimal signedQuantity, BigDecimal entryPrice,
                        Instant observedAt, String providerReference) {
            this(instrument, signedQuantity, entryPrice, observedAt, providerReference, null, null);
        }

        public Position {
            if (instrument == null || instrument.isBlank()) throw new IllegalArgumentException("instrument is required");
            if (signedQuantity == null || signedQuantity.signum() == 0) throw new IllegalArgumentException("signedQuantity must be non-zero");
            if (entryPrice == null) throw new IllegalArgumentException("entryPrice is required");
            if (observedAt == null || providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("position identity is required");
            if (margin != null && margin.signum() < 0) throw new IllegalArgumentException("margin cannot be negative");
            if (stopLoss != null && stopLoss.signum() <= 0) throw new IllegalArgumentException("stopLoss must be positive");
        }
    }

    public record Order(String providerReference, String clientReference, String status,
                        BigDecimal quantity, BigDecimal filledQuantity, Instant observedAt) {
        public Order {
            if (providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("order identity is required");
            if (quantity == null || filledQuantity == null || observedAt == null) throw new IllegalArgumentException("order facts are required");
        }
    }

    public record Deal(String providerReference, String instrument, BigDecimal quantity,
                       String side, BigDecimal price, BigDecimal fee, BigDecimal swap, BigDecimal realizedPnl,
                       Instant executedAt) {
        public Deal {
            if (providerReference == null || providerReference.isBlank()) throw new IllegalArgumentException("deal identity is required");
            if (instrument == null || instrument.isBlank() || quantity == null || price == null || executedAt == null) {
                throw new IllegalArgumentException("deal facts are required");
            }
        }
    }
}
