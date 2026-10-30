package com.hope.trading.market_data.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds segment metadata without changing canonical evidence. Unknown legacy
 * ranges remain protected until a later governed classification step.
 */
@Service
@Slf4j
public class OrderBookEvidenceSegmentBackfillService {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Clock clock;
    private final OrderBookRetentionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock backfillLock = new ReentrantLock();

    public OrderBookEvidenceSegmentBackfillService(JdbcTemplate jdbc, DataSource dataSource, Clock clock,
                                                   OrderBookRetentionProperties properties,
                                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.clock = clock;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BackfillResult backfill(int sequenceBatchSize, int boundaryBatchSize) {
        if (sequenceBatchSize < 1 || boundaryBatchSize < 1) {
            throw new IllegalArgumentException("backfill batch sizes must be positive");
        }
        if (sequenceBatchSize > properties.getBackfillMaxSequenceBatchSize()
                || boundaryBatchSize > properties.getBackfillMaxBoundaryBatchSize()) {
            throw new IllegalArgumentException("backfill batch sizes exceed configured maximums");
        }
        if (!backfillLock.tryLock()) {
            throw new BackfillInProgressException();
        }
        Instant startedAt = clock.instant();
        try {
            List<UUID> sequences = jdbc.queryForList("""
                    SELECT sequence_id
                      FROM historical_order_book_sequence
                     WHERE backfill_completed_at IS NULL
                     ORDER BY sequence_id
                     LIMIT ?
                    """, UUID.class, sequenceBatchSize);
            Counters counters = new Counters();
            for (UUID sequenceId : sequences) {
                SequenceResult result = transactionTemplate.execute(status -> backfillSequenceInternal(sequenceId, boundaryBatchSize));
                if (result == null) {
                    continue;
                }
                counters.add(result);
            }
            boolean hasMoreWork = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM historical_order_book_sequence
                         WHERE backfill_completed_at IS NULL
                    )
                    """, Boolean.class));
            BackfillResult result = counters.result(sequences.size(), hasMoreWork,
                    Duration.between(startedAt, clock.instant()));
                    log.info("Order-book segment backfill completed sequenceBatchSize={} boundaryBatchSize={} "
                            + "sequencesExamined={} sequencesCompleted={} segmentsCreated={} "
                            + "segmentsUpdated={} evidenceRowsExamined={} legacyClassified={} legacyUnknown={} "
                            + "hasMoreWork={} durationMs={}",
                    sequenceBatchSize, boundaryBatchSize, result.sequencesExamined(), result.sequencesCompleted(),
                    result.segmentsCreated(), result.segmentsUpdated(), result.evidenceRowsExamined(),
                    result.legacyClassified(), result.legacyUnknown(), result.hasMoreWork(),
                    result.duration().toMillis());
            return result;
        } finally {
            backfillLock.unlock();
        }
    }

    public void backfillSequence(UUID sequenceId, int boundaryBatchSize) {
        if (boundaryBatchSize < 1 || boundaryBatchSize > properties.getBackfillMaxBoundaryBatchSize()) {
            throw new IllegalArgumentException("boundaryBatchSize exceeds configured maximum");
        }
        if (!backfillLock.tryLock()) {
            throw new BackfillInProgressException();
        }
        try {
            transactionTemplate.executeWithoutResult(status -> backfillSequenceInternal(sequenceId, boundaryBatchSize));
        } finally {
            backfillLock.unlock();
        }
    }

    private SequenceResult backfillSequenceInternal(UUID sequenceId, int boundaryBatchSize) {
        List<UUID> sequences = jdbc.queryForList("""
                SELECT sequence_id
                  FROM historical_order_book_sequence
                 WHERE sequence_id = ?
                 FOR UPDATE
                """, UUID.class, sequenceId);
        if (sequences.isEmpty()) {
            return new SequenceResult(false, 0, 0, 0, 0, 0);
        }
        long evidenceRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence WHERE sequence_id = ?
                """, Long.class, sequenceId);
        SequenceContext context = jdbc.query("""
                SELECT sequence.sequence_id, sequence.market_id, sequence.depth, sequence.status,
                       sequence.backfill_completed_at,
                       first_evidence.provider, first_evidence.provider_symbol,
                       first_evidence.sequence_ordinal, first_evidence.known_at
                  FROM historical_order_book_sequence sequence
                  JOIN historical_order_book_evidence first_evidence
                    ON first_evidence.sequence_id = sequence.sequence_id
                   AND NOT EXISTS (
                       SELECT 1
                         FROM historical_order_book_evidence earlier_evidence
                        WHERE earlier_evidence.sequence_id = first_evidence.sequence_id
                          AND earlier_evidence.sequence_ordinal < first_evidence.sequence_ordinal
                   )
                 WHERE sequence.sequence_id = ?
                 FOR UPDATE
                """, (result, row) -> new SequenceContext(
                 result.getObject("sequence_id", UUID.class), result.getObject("market_id", UUID.class),
                 result.getInt("depth"), result.getString("status"), result.getTimestamp("backfill_completed_at"),
                 result.getString("provider"),
                 result.getString("provider_symbol"), result.getLong("sequence_ordinal"),
                 result.getTimestamp("known_at").toInstant()), sequenceId)
                 .stream().findFirst().orElse(null);
        if (context == null) {
            markBackfillComplete(sequenceId);
            return new SequenceResult(true, evidenceRows, 0, 0, 0, 0);
        }
        if (context.backfillCompletedAt() != null) {
            return existingSequenceResult(sequenceId, evidenceRows);
        }
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence_segment WHERE sequence_id = ?
                """, Integer.class, sequenceId) > 0) {
            markBackfillComplete(sequenceId);
            return existingSequenceResult(sequenceId, evidenceRows);
        }
        Instant now = clock.instant();
        long startOrdinal = context.firstOrdinal();
        Instant startKnownAt = context.firstKnownAt();
        long lastBoundary = Long.MIN_VALUE;
        int segmentsCreated = 0;
        int segmentsUpdated = 0;
        while (true) {
            List<Boundary> boundaries = jdbc.query("""
                    SELECT evidence_id, sequence_ordinal, known_at, origin
                      FROM historical_order_book_evidence
                     WHERE sequence_id = ? AND trusted = true AND evidence_type = 'SNAPSHOT'
                       AND sequence_ordinal > ?
                     ORDER BY sequence_ordinal
                     LIMIT ?
                    """, (result, row) -> new Boundary(
                    result.getObject("evidence_id", UUID.class), result.getLong("sequence_ordinal"),
                    result.getTimestamp("known_at").toInstant(), result.getString("origin")),
                    sequenceId, lastBoundary, boundaryBatchSize);
            if (boundaries.isEmpty()) {
                break;
            }
            for (Boundary boundary : boundaries) {
                segmentsCreated += insertSegment(context, startOrdinal, null, startKnownAt, null, null, null,
                        "OPEN", "LEGACY_UNKNOWN", now, null) ? 1 : 0;
                segmentsUpdated += closeSegment(context.sequenceId(), startOrdinal, boundary.sequenceOrdinal() - 1,
                        boundary.knownAt(), boundary.evidenceId(), boundary.origin(), now);
                startOrdinal = boundary.sequenceOrdinal();
                startKnownAt = boundary.knownAt();
                lastBoundary = boundary.sequenceOrdinal();
            }
        }
        Long lastOrdinal = jdbc.queryForObject("""
                SELECT MAX(sequence_ordinal)
                  FROM historical_order_book_evidence
                 WHERE sequence_id = ?
                """, Long.class, sequenceId);
        if (lastOrdinal == null) {
            markBackfillComplete(sequenceId);
            return new SequenceResult(true, evidenceRows, segmentsCreated, segmentsUpdated, 0, segmentsCreated);
        }
        if ("ACTIVE".equals(context.status())) {
            segmentsCreated += insertSegment(context, startOrdinal, null, startKnownAt, null, null, null,
                    "OPEN", "LEGACY_UNKNOWN", now, null) ? 1 : 0;
        } else {
            segmentsCreated += insertSegment(context, startOrdinal, lastOrdinal, startKnownAt, null, null, null,
                    "CLOSED", "LEGACY_UNKNOWN", now, now) ? 1 : 0;
        }
        markBackfillComplete(sequenceId);
        return new SequenceResult(true, evidenceRows, segmentsCreated, segmentsUpdated, 0, segmentsCreated);
    }

    private SequenceResult existingSequenceResult(UUID sequenceId, long evidenceRows) {
        long legacyUnknown = jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence_segment
                 WHERE sequence_id = ? AND classification = 'LEGACY_UNKNOWN'
                """, Long.class, sequenceId);
        return new SequenceResult(true, evidenceRows, 0, 0, 0, legacyUnknown);
    }

    private boolean insertSegment(SequenceContext context, long startOrdinal, Long endOrdinal,
                               Instant startKnownAt, Instant endKnownAt, UUID successorEvidenceId,
                               String successorOrigin, String status, String classification,
                               Instant createdAt, Instant closedAt) {
        UUID segmentId = id(context.sequenceId(), startOrdinal);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM historical_order_book_evidence_segment WHERE segment_id = ?)
                """, Boolean.class, segmentId))) {
            return false;
        }
        String insert = """
                INSERT INTO historical_order_book_evidence_segment (
                    segment_id, market_id, provider, provider_symbol, depth, sequence_id,
                    start_ordinal, end_ordinal, start_known_at, end_known_at,
                    successor_evidence_id, successor_origin, status, classification, created_at, closed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String sql = isH2()
                ? insert.replace("INSERT INTO", "MERGE INTO")
                    .replace(") VALUES", ") KEY(sequence_id, start_ordinal) VALUES")
                : insert + " ON CONFLICT (sequence_id, start_ordinal) DO NOTHING";
        return jdbc.update(sql, segmentId, context.marketId(), context.provider(),
                context.providerSymbol(), context.depth(), context.sequenceId(), startOrdinal, endOrdinal,
                Timestamp.from(startKnownAt), timestamp(endKnownAt), successorEvidenceId, successorOrigin,
                status, classification, Timestamp.from(createdAt), timestamp(closedAt)) == 1;
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    private int closeSegment(UUID sequenceId, long startOrdinal, long endOrdinal, Instant endKnownAt,
                              UUID successorEvidenceId, String successorOrigin, Instant closedAt) {
        if (endOrdinal < startOrdinal) {
            return 0;
        }
        return jdbc.update("""
                UPDATE historical_order_book_evidence_segment
                   SET end_ordinal = ?, end_known_at = ?, successor_evidence_id = ?,
                       successor_origin = ?, status = 'CLOSED', closed_at = ?
                 WHERE sequence_id = ? AND start_ordinal = ?
                   AND classification = 'LEGACY_UNKNOWN' AND status = 'OPEN'
                """, endOrdinal, Timestamp.from(endKnownAt), successorEvidenceId, successorOrigin,
                Timestamp.from(closedAt), sequenceId, startOrdinal);
    }

    private void markBackfillComplete(UUID sequenceId) {
        jdbc.update("""
                UPDATE historical_order_book_sequence
                   SET backfill_completed_at = ?
                 WHERE sequence_id = ? AND backfill_completed_at IS NULL
                """, Timestamp.from(clock.instant()), sequenceId);
    }

    private UUID id(UUID sequenceId, long startOrdinal) {
        return UUID.nameUUIDFromBytes(("order-book-segment-v1:" + sequenceId + ":" + startOrdinal)
                .getBytes(StandardCharsets.UTF_8));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record SequenceContext(UUID sequenceId, UUID marketId, int depth, String status,
                                   Timestamp backfillCompletedAt,
                                   String provider, String providerSymbol, long firstOrdinal,
                                   Instant firstKnownAt) {
    }

    private record Boundary(UUID evidenceId, long sequenceOrdinal, Instant knownAt, String origin) {
    }

    private record SequenceResult(boolean completed, long evidenceRows, int segmentsCreated,
                                  int segmentsUpdated, long legacyClassified, long legacyUnknown) {
    }

    private static final class Counters {
        private int sequencesCompleted;
        private long evidenceRows;
        private int segmentsCreated;
        private int segmentsUpdated;
        private long legacyClassified;
        private long legacyUnknown;

        private void add(SequenceResult result) {
            evidenceRows += result.evidenceRows();
            segmentsCreated += result.segmentsCreated();
            segmentsUpdated += result.segmentsUpdated();
            legacyClassified += result.legacyClassified();
            legacyUnknown += result.legacyUnknown();
            if (result.completed()) {
                sequencesCompleted++;
            }
        }

        private BackfillResult result(int sequencesExamined, boolean hasMoreWork, Duration duration) {
            return new BackfillResult(sequencesExamined, sequencesCompleted, evidenceRows, segmentsCreated,
                    segmentsUpdated, legacyClassified, legacyUnknown, hasMoreWork, duration);
        }
    }

    public record BackfillResult(int sequencesExamined, int sequencesCompleted, long evidenceRowsExamined,
                                 int segmentsCreated, int segmentsUpdated, long legacyClassified,
                                 long legacyUnknown, boolean hasMoreWork, Duration duration) {
    }

    public static final class BackfillInProgressException extends RuntimeException {
        public BackfillInProgressException() {
            super("order-book segment backfill is already running");
        }
    }
}
