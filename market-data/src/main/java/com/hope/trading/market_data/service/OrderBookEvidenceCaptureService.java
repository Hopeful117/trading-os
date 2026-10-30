package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookDelta;
import com.hope.trading.market_data.model.OrderBookDeltaType;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderBookEvidenceCaptureService {
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofMinutes(5);
    private final OrderBookEvidenceRepository repository;
    private final Clock clock;
    private final ConcurrentMap<OrderBookKey, ActiveSequence> sequences = new ConcurrentHashMap<>();
    private final ConcurrentMap<OrderBookKey, Object> sequenceLocks = new ConcurrentHashMap<>();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final OrderBookStoragePressureGuard pressureGuard;
    private final AtomicReference<Instant> lastSuccessfulPersistence = new AtomicReference<>();
    private volatile boolean durabilityCompromised;

    @Autowired
    public OrderBookEvidenceCaptureService(OrderBookEvidenceRepository repository, Clock clock,
                                           OrderBookStoragePressureGuard pressureGuard) {
        this.repository = repository;
        this.clock = clock;
        this.pressureGuard = pressureGuard;
    }

    public OrderBookEvidenceCaptureService(OrderBookEvidenceRepository repository, Clock clock) {
        this(repository, clock, new OrderBookStoragePressureGuard(testProperties()));
    }

    private static com.hope.trading.market_data.config.OrderBookRetentionProperties testProperties() {
        var properties = new com.hope.trading.market_data.config.OrderBookRetentionProperties();
        properties.setMinimumFreeBytes(0);
        return properties;
    }

    public boolean acceptSnapshot(OrderBookDelta delta) {
        return acceptSnapshot(delta, OrderBookEvidenceOrigin.PROVIDER);
    }

    public boolean acceptLocalSnapshot(OrderBookDelta delta) {
        return acceptSnapshot(delta, OrderBookEvidenceOrigin.LOCAL_OBSERVATION);
    }

    private boolean acceptSnapshot(OrderBookDelta delta, OrderBookEvidenceOrigin origin) {
        if (durabilityCompromised || pressureGuard.underPressure()
                || delta.type() != OrderBookDeltaType.SNAPSHOT || !validChecksum(delta)) {
            return false;
        }
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        synchronized (sequenceLocks.computeIfAbsent(key, ignored -> new Object())) {
            UUID sequenceId = UUID.randomUUID();
            Instant knownAt = knownAt();
            ActiveSequence sequence = new ActiveSequence(sequenceId, delta.marketId(), delta.depth(), 0,
                    delta.providerSequence(), null, null, delta.occurredAt(), knownAt);
            try {
                boolean persisted = repository.appendSnapshotAsNewSequence(
                        toEvidence(delta, sequence, knownAt, OrderBookEvidenceType.SNAPSHOT,
                                origin, 0), knownAt);
                if (!persisted) {
                    sequences.remove(key);
                    return false;
                }
                sequences.put(key, sequence);
                lastSuccessfulPersistence.set(knownAt);
                log.info("ORDER_BOOK_SEQUENCE_STARTED provider={} marketId={} depth={} sequenceId={} "
                                + "providerSequence={} occurredAt={} knownAt={}",
                        delta.provider(), delta.marketId(), delta.depth(), sequenceId, delta.providerSequence(),
                        delta.occurredAt(), knownAt);
                return true;
            } catch (RuntimeException exception) {
                sequences.remove(key);
                return false;
            }
        }
    }

    public boolean acceptUpdate(OrderBookDelta delta) {
        return acceptUpdate(delta, OrderBookEvidenceOrigin.PROVIDER);
    }

    public boolean acceptLocalUpdate(OrderBookDelta delta) {
        return acceptUpdate(delta, OrderBookEvidenceOrigin.LOCAL_OBSERVATION);
    }

    private boolean acceptUpdate(OrderBookDelta delta, OrderBookEvidenceOrigin origin) {
        if (durabilityCompromised || pressureGuard.underPressure()
                || delta.type() != OrderBookDeltaType.UPDATE || !validChecksum(delta)) {
            return false;
        }
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        synchronized (sequenceLocks.computeIfAbsent(key, ignored -> new Object())) {
            ActiveSequence current = sequences.get(key);
            if (current == null || !isContinuous(current.providerSequence(), delta.providerSequence())) {
                if (current != null && !tryInvalidate(key, "provider sequence gap")) {
                    return false;
                }
                sequences.remove(key);
                return false;
            }
            long ordinal = current.ordinal() + 1;
            Instant knownAt = knownAt();
            try {
                OrderBookEvidence evidence = toEvidence(delta,
                        new ActiveSequence(current.sequenceId(), current.marketId(), current.depth(), ordinal,
                                delta.providerSequence(), current.lastCheckpoint(), current.lastCheckpointOrdinal(),
                                delta.occurredAt(), knownAt), knownAt,
                        OrderBookEvidenceType.DELTA, origin, ordinal);
                boolean persisted = repository.append(evidence);
                if (!persisted && !repository.containsTrusted(evidence.contentFingerprint())) {
                    tryInvalidate(key, "evidence persistence was not confirmed");
                    sequences.remove(key);
                    return false;
                }
                sequences.put(key, new ActiveSequence(current.sequenceId(), current.marketId(), current.depth(), ordinal,
                        delta.providerSequence(), current.lastCheckpoint(), current.lastCheckpointOrdinal(),
                        delta.occurredAt(), knownAt));
                lastSuccessfulPersistence.set(knownAt);
                return true;
            } catch (RuntimeException exception) {
                tryInvalidate(key, "evidence persistence failure");
                sequences.remove(key);
                return false;
            }
        }
    }

    public void checkpoint(OrderBookDelta delta, OrderBookSnapshot snapshot) {
        OrderBookKey key = new OrderBookKey(delta.marketId(), delta.depth());
        synchronized (sequenceLocks.computeIfAbsent(key, ignored -> new Object())) {
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
                    durabilityCompromised = true;
                    tryInvalidate(key, "checkpoint persistence was not confirmed");
                    sequences.remove(key, current);
                    return;
                }
            } catch (RuntimeException exception) {
                durabilityCompromised = true;
                tryInvalidate(key, "checkpoint persistence failure");
                sequences.remove(key, current);
                return;
            }
            sequences.put(key, new ActiveSequence(current.sequenceId(), current.marketId(), current.depth(), current.ordinal(),
                    current.providerSequence(), knownAt, current.ordinal(), snapshot.occurredAt(), knownAt));
            lastSuccessfulPersistence.set(knownAt);
        }
    }

    public void invalidate(OrderBookKey key) {
        synchronized (sequenceLocks.computeIfAbsent(key, ignored -> new Object())) {
            ActiveSequence current = sequences.get(key);
            if (tryInvalidate(key, "order book state invalidated")) {
                if (current == null) {
                    sequences.remove(key);
                } else {
                    sequences.remove(key, current);
                }
            }
        }
    }

    public void seal(OrderBookKey key) {
        synchronized (sequenceLocks.computeIfAbsent(key, ignored -> new Object())) {
            ActiveSequence current = sequences.get(key);
            if (trySeal(key)) {
                sequences.remove(key, current);
            }
        }
    }

    public SequenceDiagnostics diagnosticSnapshot(OrderBookKey key) {
        ActiveSequence current = sequences.get(key);
        return current == null ? null : new SequenceDiagnostics(current.sequenceId(), current.marketId(),
                current.depth(), current.providerSequence(), current.ordinal(), current.lastOccurredAt(),
                current.lastKnownAt());
    }

    public boolean durabilityCompromised() {
        return durabilityCompromised;
    }

    public Instant lastSuccessfulPersistence() {
        return lastSuccessfulPersistence.get();
    }

    public List<SequenceDiagnostics> activeSequences() {
        return sequences.values().stream()
                .map(current -> new SequenceDiagnostics(current.sequenceId(), current.marketId(), current.depth(),
                        current.providerSequence(), current.ordinal(), current.lastOccurredAt(), current.lastKnownAt()))
                .sorted(Comparator.comparing(SequenceDiagnostics::sequenceId))
                .toList();
    }

    private boolean tryInvalidate(OrderBookKey key, String reason) {
        try {
            repository.invalidateActiveSequences(key.marketId(), key.depth(), reason, clock.instant());
            return true;
        } catch (RuntimeException ignored) {
            durabilityCompromised = true;
            log.error("Unable to persist order-book sequence invalidation key={} reason={}",
                    key, reason, ignored);
            return false;
        }
    }

    private boolean trySeal(OrderBookKey key) {
        Instant sealedAt = clock.instant();
        try {
            repository.sealActiveSequences(key.marketId(), key.depth(), sealedAt);
            return true;
        } catch (RuntimeException exception) {
            durabilityCompromised = true;
            log.error("Unable to persist order-book sequence seal key={}", key, exception);
            try {
                repository.invalidateActiveSequences(key.marketId(), key.depth(),
                        "sequence sealing persistence failure", sealedAt);
                return true;
            } catch (RuntimeException invalidationException) {
                log.error("Unable to fail closed after order-book sequence seal failure key={}", key,
                        invalidationException);
                return false;
            }
        }
    }

    private OrderBookEvidence toEvidence(
            OrderBookDelta delta,
            ActiveSequence sequence,
            Instant knownAt,
            OrderBookEvidenceType type,
            OrderBookEvidenceOrigin origin,
            long ordinal
    ) {
        OrderBookEvidence evidence = new OrderBookEvidence(
                UUID.randomUUID(), delta.marketId(), delta.provider(), delta.symbol(),
                delta.providerSymbol(), delta.depth(), type, origin, null, null, sequence.sequenceId(), ordinal,
                delta.providerSequence(), delta.occurredAt(), knownAt, delta.bids(),
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
            UUID marketId,
            int depth,
            long ordinal,
            Long providerSequence,
            Instant lastCheckpoint,
            Long lastCheckpointOrdinal,
            Instant lastOccurredAt,
            Instant lastKnownAt
    ) {
    }

    public record SequenceDiagnostics(
            UUID sequenceId,
            UUID marketId,
            int depth,
            Long providerSequence,
            long localOrdinal,
            Instant lastOccurredAt,
            Instant lastKnownAt
    ) {
    }
}
