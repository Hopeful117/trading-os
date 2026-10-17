package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReferenceMarketState(
        UUID marketId,
        String instrument,
        Instant evaluationCutoff,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal referencePrice,
        EconomicProductType productType,
        Instant knownAt,
        String evidenceFingerprint,
        List<Level> bids,
        List<Level> asks,
        UUID sequenceId,
        Long sourceStartOrdinal,
        Long sourceEndOrdinal,
        String provider,
        String providerSymbol,
        String normalizationVersion
) {
    public ReferenceMarketState(
            UUID marketId,
            String instrument,
            Instant evaluationCutoff,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal referencePrice,
            Instant knownAt,
            String evidenceFingerprint
    ) {
        this(marketId, instrument, evaluationCutoff, bid, ask, referencePrice,
                EconomicProductType.SPOT, knownAt, evidenceFingerprint, List.of(), List.of(),
                null, null, null, null, null, null);
    }

    public ReferenceMarketState(
            UUID marketId,
            String instrument,
            Instant evaluationCutoff,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal referencePrice,
            EconomicProductType productType,
            Instant knownAt,
            String evidenceFingerprint
    ) {
        this(marketId, instrument, evaluationCutoff, bid, ask, referencePrice, productType,
                knownAt, evidenceFingerprint, List.of(), List.of(), null, null, null, null, null, null);
    }

    public ReferenceMarketState(
            UUID marketId,
            String instrument,
            Instant evaluationCutoff,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal referencePrice,
            EconomicProductType productType,
            Instant knownAt,
            String evidenceFingerprint,
            List<Level> bids,
            List<Level> asks,
            UUID sequenceId,
            Long sourceStartOrdinal,
            Long sourceEndOrdinal,
            String provider,
            String providerSymbol,
            String normalizationVersion
    ) {
        instrument = required(instrument, "instrument");
        evidenceFingerprint = required(evidenceFingerprint, "evidenceFingerprint");
        this.marketId = marketId;
        this.instrument = instrument;
        this.evaluationCutoff = evaluationCutoff;
        this.bid = bid;
        this.ask = ask;
        this.referencePrice = referencePrice;
        this.productType = productType;
        this.knownAt = knownAt;
        this.evidenceFingerprint = evidenceFingerprint;
        this.bids = bids == null ? List.of() : List.copyOf(bids);
        this.asks = asks == null ? List.of() : List.copyOf(asks);
        this.sequenceId = sequenceId;
        this.sourceStartOrdinal = sourceStartOrdinal;
        this.sourceEndOrdinal = sourceEndOrdinal;
        this.provider = provider;
        this.providerSymbol = providerSymbol;
        this.normalizationVersion = normalizationVersion;
        validate();
    }

    private void validate() {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff is required");
        requirePositiveIfPresent(bid, "bid");
        requirePositiveIfPresent(ask, "ask");
        requirePositive(referencePrice, "referencePrice");
        Objects.requireNonNull(productType, "productType is required");
        Objects.requireNonNull(knownAt, "knownAt is required");
        Objects.requireNonNull(evidenceFingerprint, "evidenceFingerprint is required");
        if (bid != null && ask != null && bid.compareTo(ask) > 0) {
            throw new IllegalArgumentException("bid cannot exceed ask");
        }
        if ((bid != null && referencePrice.compareTo(bid) < 0)
                || (ask != null && referencePrice.compareTo(ask) > 0)) {
            throw new IllegalArgumentException("referencePrice must be inside the quoted spread");
        }
        validateLevels(bids, true);
        validateLevels(asks, false);
        if (!bids.isEmpty() && (bid == null || bids.get(0).price().compareTo(bid) != 0)) {
            throw new IllegalArgumentException("best bid must match the first visible bid level");
        }
        if (!asks.isEmpty() && (ask == null || asks.get(0).price().compareTo(ask) != 0)) {
            throw new IllegalArgumentException("best ask must match the first visible ask level");
        }
    }

    private static void validateLevels(List<Level> levels, boolean bids) {
        Comparator<Level> comparator = Comparator.comparing(Level::price);
        if (bids) comparator = comparator.reversed();
        for (int index = 0; index < levels.size(); index++) {
            Level level = Objects.requireNonNull(levels.get(index), "level is required");
            if (index > 0 && comparator.compare(levels.get(index - 1), level) > 0) {
                throw new IllegalArgumentException("visible levels must be deterministically ordered");
            }
            if (level.price().signum() <= 0 || level.quantity().signum() < 0) {
                throw new IllegalArgumentException("visible level price must be positive and quantity non-negative");
            }
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (Objects.requireNonNull(value, field).signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requirePositiveIfPresent(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive when present");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    public record Level(BigDecimal price, BigDecimal quantity) {
        public Level {
            Objects.requireNonNull(price, "level price is required");
            Objects.requireNonNull(quantity, "level quantity is required");
        }
    }
}
