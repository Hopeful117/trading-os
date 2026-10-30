package com.hope.trading.market_data.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds segment metadata without changing canonical evidence. Unknown legacy
 * ranges remain protected until a later governed classification step.
 */
@Service
public class OrderBookEvidenceSegmentBackfillService {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Clock clock;

    public OrderBookEvidenceSegmentBackfillService(JdbcTemplate jdbc, DataSource dataSource, Clock clock) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public int backfill(int sequenceBatchSize, int boundaryBatchSize) {
        if (sequenceBatchSize < 1 || boundaryBatchSize < 1) {
            throw new IllegalArgumentException("backfill batch sizes must be positive");
        }
        List<UUID> sequences = jdbc.queryForList("""
                SELECT sequence_id
                  FROM historical_order_book_sequence
                 WHERE backfill_completed_at IS NULL
                 ORDER BY sequence_id
                 LIMIT ?
                """, UUID.class, sequenceBatchSize);
        sequences.forEach(sequenceId -> backfillSequence(sequenceId, boundaryBatchSize));
        return sequences.size();
    }

    @Transactional
    public void backfillSequence(UUID sequenceId, int boundaryBatchSize) {
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
                """, (result, row) -> new SequenceContext(
                 result.getObject("sequence_id", UUID.class), result.getObject("market_id", UUID.class),
                 result.getInt("depth"), result.getString("status"), result.getTimestamp("backfill_completed_at"),
                 result.getString("provider"),
                result.getString("provider_symbol"), result.getLong("sequence_ordinal"),
                result.getTimestamp("known_at").toInstant()), sequenceId)
                .stream().findFirst().orElse(null);
        if (context == null) {
            return;
        }
        if (context.backfillCompletedAt() != null) {
            return;
        }
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence_segment WHERE sequence_id = ?
                """, Integer.class, sequenceId) > 0) {
            markBackfillComplete(sequenceId);
            return;
        }
        Instant now = clock.instant();
        long startOrdinal = context.firstOrdinal();
        Instant startKnownAt = context.firstKnownAt();
        long lastBoundary = Long.MIN_VALUE;
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
                insertSegment(context, startOrdinal, null, startKnownAt, null, null, null,
                        "OPEN", "LEGACY_UNKNOWN", now, null);
                closeSegment(context.sequenceId(), startOrdinal, boundary.sequenceOrdinal() - 1,
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
            return;
        }
        if ("ACTIVE".equals(context.status())) {
            insertSegment(context, startOrdinal, null, startKnownAt, null, null, null,
                    "OPEN", "LEGACY_UNKNOWN", now, null);
        } else {
            insertSegment(context, startOrdinal, lastOrdinal, startKnownAt, null, null, null,
                    "CLOSED", "LEGACY_UNKNOWN", now, now);
        }
        markBackfillComplete(sequenceId);
    }

    private void insertSegment(SequenceContext context, long startOrdinal, Long endOrdinal,
                               Instant startKnownAt, Instant endKnownAt, UUID successorEvidenceId,
                               String successorOrigin, String status, String classification,
                               Instant createdAt, Instant closedAt) {
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
        jdbc.update(sql, id(context.sequenceId(), startOrdinal), context.marketId(), context.provider(),
                context.providerSymbol(), context.depth(), context.sequenceId(), startOrdinal, endOrdinal,
                Timestamp.from(startKnownAt), timestamp(endKnownAt), successorEvidenceId, successorOrigin,
                status, classification, Timestamp.from(createdAt), timestamp(closedAt));
    }

    private boolean isH2() {
        try {
            return dataSource.getConnection().getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    private void closeSegment(UUID sequenceId, long startOrdinal, long endOrdinal, Instant endKnownAt,
                              UUID successorEvidenceId, String successorOrigin, Instant closedAt) {
        if (endOrdinal < startOrdinal) {
            return;
        }
        jdbc.update("""
                UPDATE historical_order_book_evidence_segment
                   SET end_ordinal = ?, end_known_at = ?, successor_evidence_id = ?,
                       successor_origin = ?, status = 'CLOSED', closed_at = ?
                 WHERE sequence_id = ? AND start_ordinal = ?
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
}
