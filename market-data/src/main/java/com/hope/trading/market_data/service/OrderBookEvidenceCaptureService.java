package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderBookEvidenceCaptureService {
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofMinutes(5);
    private final OrderBookEvidenceRepository repository;
    private final Clock clock;
    private final ConcurrentMap<OrderBookKey, ActiveSequence> sequences = new ConcurrentHashMap<>();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private volatile boolean durabilityCompromised;

    public OrderBookEvidenceCaptureService(OrderBookEvidenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean acceptSnapshot(OrderBookDelta delta) {
        if (durabilityCompromised || delta.type() != OrderBookDeltaType.SNAPSHOT || !validChecksum(delta)) {
            return false;
        }
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        UUID sequenceId = UUID.randomUUID();
        ActiveSequence sequence = new ActiveSequence(sequenceId, 0, delta.providerSequence(), null, null);
        try {
            repository.append(toEvidence(delta, sequence, OrderBookEvidenceType.SNAPSHOT,
                    OrderBookEvidenceOrigin.PROVIDER, 0));
            sequences.put(key, sequence);
            return true;
        } catch (RuntimeException exception) {
            sequences.remove(key);
            return false;
        }
    }

    public boolean acceptUpdate(OrderBookDelta delta) {
        if (durabilityCompromised || delta.type() != OrderBookDeltaType.UPDATE || !validChecksum(delta)) {
            return false;
        }
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        ActiveSequence current = sequences.get(key);
        if (current == null || !isContinuous(current.providerSequence(), delta.providerSequence())) {
            if (current != null && !tryInvalidate(current.sequenceId(), "provider sequence gap")) {
                return false;
            }
            sequences.remove(key);
            return false;
        }
        long ordinal = current.ordinal() + 1;
        try {
            repository.append(toEvidence(delta,
                    new ActiveSequence(current.sequenceId(), ordinal, delta.providerSequence(), current.lastCheckpoint(),
                            current.lastCheckpointOrdinal()),
                    OrderBookEvidenceType.DELTA, OrderBookEvidenceOrigin.PROVIDER, ordinal));
            sequences.put(key, new ActiveSequence(current.sequenceId(), ordinal,
                    delta.providerSequence(), current.lastCheckpoint(), current.lastCheckpointOrdinal()));
            return true;
        } catch (RuntimeException exception) {
            tryInvalidate(current.sequenceId(), "evidence persistence failure");
            sequences.remove(key);
            return false;
        }
    }

    public void checkpoint(OrderBookDelta delta, OrderBookSnapshot snapshot) {
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        ActiveSequence current = sequences.get(key);
        if (current == null || !shouldCheckpoint(current.lastCheckpoint())) {
            return;
        }
        Instant knownAt = knownAt();
        OrderBookEvidence checkpoint = new OrderBookEvidence(
                UUID.randomUUID(), delta.marketId(), delta.provider(), snapshot.symbol(),
                delta.providerSymbol(), delta.depth(), OrderBookEvidenceType.SNAPSHOT,
                OrderBookEvidenceOrigin.CHECKPOINT,
                current.lastCheckpointOrdinal() == null ? 0L : current.lastCheckpointOrdinal(),
                current.ordinal(), current.sequenceId(), current.ordinal(),
                current.providerSequence(), snapshot.occurredAt(), knownAt, snapshot.bids(),
                snapshot.asks(), delta.checksum(), OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        OrderBookEvidence persistedCheckpoint = withFingerprint(checkpoint);
        try {
            boolean inserted = repository.append(persistedCheckpoint);
            if (!inserted && !repository.containsTrusted(persistedCheckpoint.contentFingerprint())) {
                tryInvalidate(current.sequenceId(), "checkpoint persistence was not confirmed");
                return;
            }
        } catch (RuntimeException exception) {
            tryInvalidate(current.sequenceId(), "checkpoint persistence failure");
            return;
        }
        sequences.put(key, new ActiveSequence(current.sequenceId(), current.ordinal(),
                current.providerSequence(), knownAt, current.ordinal()));
    }

    public void invalidate(OrderBookKey key) {
        ActiveSequence current = sequences.get(key);
        if (current != null && tryInvalidate(current.sequenceId(), "order book state invalidated")) {
            sequences.remove(key, current);
        }
    }

    private boolean tryInvalidate(UUID sequenceId, String reason) {
        try {
            repository.invalidateSequence(sequenceId, reason, clock.instant());
            return true;
        } catch (RuntimeException ignored) {
            durabilityCompromised = true;
            log.error("Unable to persist order-book sequence invalidation sequenceId={} reason={}",
                    sequenceId, reason, ignored);
            return false;
        }
    }

    private OrderBookEvidence toEvidence(
            OrderBookDelta delta,
            ActiveSequence sequence,
            OrderBookEvidenceType type,
            OrderBookEvidenceOrigin origin,
            long ordinal
    ) {
        OrderBookEvidence evidence = new OrderBookEvidence(
                UUID.randomUUID(), delta.marketId(), delta.provider(), delta.symbol(),
                delta.providerSymbol(), delta.depth(), type, origin, null, null, sequence.sequenceId(), ordinal,
                delta.providerSequence(), delta.occurredAt(), knownAt(), delta.bids(),
                delta.asks(), delta.checksum(), OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return withFingerprint(evidence);
    }

    private OrderBookEvidence withFingerprint(OrderBookEvidence evidence) {
        return new OrderBookEvidence(evidence.evidenceId(), evidence.marketId(), evidence.provider(),
                evidence.symbol(), evidence.providerSymbol(), evidence.depth(), evidence.evidenceType(),
                evidence.origin(), evidence.sourceStartOrdinal(), evidence.sourceEndOrdinal(),
                evidence.sequenceId(), evidence.sequenceOrdinal(), evidence.providerSequence(),
                evidence.occurredAt(), evidence.knownAt(), evidence.bids(), evidence.asks(), evidence.checksum(),
                evidence.normalizationVersion(), OrderBookEvidenceFingerprint.fingerprint(evidence));
    }

    private Instant knownAt() {
        return clock.instant();
    }

    private boolean validChecksum(OrderBookDelta delta) {
        // The current Futures adapter does not yet expose the provider checksum
        // algorithm. Never mark an explicitly supplied, unvalidated checksum as
        // trusted evidence; feeds without a checksum remain sequence-validated.
        return delta.checksum() == null;
    }

    private boolean isContinuous(Long previous, Long current) {
        return previous == null || current == null || current == previous + 1;
    }

    private boolean shouldCheckpoint(Instant lastCheckpoint) {
        return lastCheckpoint == null || !lastCheckpoint.plus(CHECKPOINT_INTERVAL).isAfter(clock.instant());
    }

    private record ActiveSequence(
            UUID sequenceId,
            long ordinal,
            Long providerSequence,
            Instant lastCheckpoint,
            Long lastCheckpointOrdinal
    ) {
    }
}
