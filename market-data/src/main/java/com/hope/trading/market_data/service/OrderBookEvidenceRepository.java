package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceSegment;
import com.hope.trading.market_data.model.OrderBookEvidenceSegmentClassification;
import com.hope.trading.market_data.model.OrderBookEvidenceSegmentStatus;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Repository
public class OrderBookEvidenceRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public OrderBookEvidenceRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Transactional
    public boolean append(OrderBookEvidence evidence) {
        requireTrustedEvidence(evidence);
        ensureSequence(evidence);
        return insertEvidence(evidence);
    }

    @Transactional
    public boolean appendSnapshotAsNewSequence(OrderBookEvidence evidence, Instant supersededAt) {
        requireTrustedEvidence(evidence);
        if (exists(evidence.contentFingerprint())) {
            return false;
        }
        ensureSequence(evidence);
        if (!insertEvidence(evidence)) {
            throw new IllegalStateException("Unable to persist new order-book snapshot sequence");
        }
        sealOtherActiveSequences(evidence.marketId(), evidence.depth(), evidence.sequenceId(),
                "superseded by fresh provider snapshot", supersededAt);
        openSegment(evidence, supersededAt);
        return true;
    }

    private boolean insertEvidence(OrderBookEvidence evidence) {
        if (isH2() && exists(evidence.contentFingerprint())) {
            return false;
        }
        int rows = jdbc.update(evidenceInsertSql(),
                evidence.evidenceId(), evidence.marketId(), evidence.provider().name(), evidence.symbol(),
                evidence.providerSymbol(), evidence.depth(), evidence.evidenceType().name(),
                evidence.origin().name(), evidence.sourceStartOrdinal(), evidence.sourceEndOrdinal(),
                evidence.sequenceId(), evidence.sequenceOrdinal(),
                evidence.providerSequence(), Timestamp.from(evidence.occurredAt()),
                Timestamp.from(evidence.knownAt()), OrderBookEvidencePayload.encode(evidence.bids()),
                OrderBookEvidencePayload.encode(evidence.asks()), evidence.checksum(),
                evidence.normalizationVersion(), evidence.contentFingerprint(), true);
        if (rows == 1 && evidence.evidenceType() == OrderBookEvidenceType.SNAPSHOT) {
            rotateSegment(evidence, evidence.knownAt());
        }
        return rows == 1;
    }

    private void requireTrustedEvidence(OrderBookEvidence evidence) {
        if (evidence.checksum() != null) {
            throw new IllegalArgumentException("unvalidated provider checksum cannot be trusted");
        }
        if (!OrderBookEvidenceFingerprint.NORMALIZATION_VERSION.equals(evidence.normalizationVersion())) {
            throw new IllegalArgumentException("unsupported order-book normalization version");
        }
        if (!OrderBookEvidenceFingerprint.fingerprint(evidence).equals(evidence.contentFingerprint())) {
            throw new IllegalArgumentException("order-book evidence fingerprint does not match canonical content");
        }
    }

    private void ensureSequence(OrderBookEvidence evidence) {
        if (isH2() && existsSequence(evidence.sequenceId())) {
            return;
        }
        jdbc.update(sequenceInsertSql(), evidence.sequenceId(), evidence.marketId(), evidence.depth());
    }

    private boolean existsSequence(UUID sequenceId) {
        return !jdbc.queryForList(
                "SELECT sequence_id FROM historical_order_book_sequence WHERE sequence_id = ?",
                UUID.class, sequenceId).isEmpty();
    }

    private boolean exists(String fingerprint) {
        return !jdbc.queryForList(
                "SELECT content_fingerprint FROM historical_order_book_evidence WHERE content_fingerprint = ?",
                String.class, fingerprint).isEmpty();
    }

    private String sequenceInsertSql() {
        if (isH2()) {
            return """
                    MERGE INTO historical_order_book_sequence (
                        sequence_id, market_id, depth, status
                    ) KEY(sequence_id) VALUES (?, ?, ?, 'ACTIVE')
                    """;
        }
        return """
                INSERT INTO historical_order_book_sequence (
                    sequence_id, market_id, depth, status
                ) VALUES (?, ?, ?, 'ACTIVE')
                ON CONFLICT (sequence_id) DO NOTHING
                """;
    }

    private String evidenceInsertSql() {
        String columns = """
                (evidence_id, market_id, provider, symbol, provider_symbol, depth,
                 evidence_type, origin, source_start_ordinal, source_end_ordinal,
                 sequence_id, sequence_ordinal, provider_sequence,
                 occurred_at, known_at, bids_payload, asks_payload, checksum,
                 normalization_version, content_fingerprint, trusted)
                """;
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "MERGE INTO historical_order_book_evidence " + columns
                    + " KEY(content_fingerprint) " + values;
        }
        return "INSERT INTO historical_order_book_evidence " + columns + " " + values
                + " ON CONFLICT (content_fingerprint) DO NOTHING";
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    @Transactional
    public void invalidateSequence(UUID sequenceId, String reason, Instant invalidatedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence target
                        SET status = 'INVALIDATED', sealed_at = NULL, invalidated_at = ?, invalidation_reason = ?
                        WHERE target.sequence_id = ? AND target.status = 'ACTIVE'
                        """,
                Timestamp.from(invalidatedAt), reason, sequenceId);
        closeSegmentsForSequence(sequenceId, invalidatedAt);
    }

    @Transactional
    public void invalidateActiveSequences(UUID marketId, int depth, String reason, Instant invalidatedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'INVALIDATED', sealed_at = NULL, invalidated_at = ?, invalidation_reason = ?
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE'
                        """,
                Timestamp.from(invalidatedAt), reason, marketId, depth);
        closeSegmentsForSequences(marketId, depth, invalidatedAt);
    }

    @Transactional
    public void sealActiveSequences(UUID marketId, int depth, Instant sealedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'SEALED', sealed_at = ?, invalidated_at = NULL, invalidation_reason = NULL
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE'
                        """,
                Timestamp.from(sealedAt), marketId, depth);
        closeSegmentsForSequences(marketId, depth, sealedAt);
    }

    private void sealOtherActiveSequences(UUID marketId, int depth, UUID sequenceId,
                                           String ignoredReason, Instant sealedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'SEALED', sealed_at = ?, invalidated_at = NULL, invalidation_reason = NULL
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE' AND sequence_id <> ?
                        """,
                        Timestamp.from(sealedAt), marketId, depth, sequenceId);
        jdbc.queryForList("""
                        SELECT sequence_id
                          FROM historical_order_book_sequence
                         WHERE market_id = ? AND depth = ? AND status = 'SEALED' AND sequence_id <> ?
                        """, UUID.class, marketId, depth, sequenceId)
                .forEach(id -> closeSegmentsForSequence(id, sealedAt));
    }

    @Transactional(readOnly = true)
    public boolean containsTrusted(String fingerprint) {
        return !jdbc.queryForList("""
                        SELECT evidence_id
                        FROM historical_order_book_evidence
                        WHERE content_fingerprint = ? AND trusted = true
                        """, UUID.class, fingerprint).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<OrderBookEvidence> findTrusted(UUID marketId, int depth, Instant cutoff) {
        Optional<OrderBookEvidence> anchor = findTrustedReplayBase(marketId, depth, cutoff);
        if (anchor.isEmpty()) return List.of();

        List<OrderBookEvidence> result = new java.util.ArrayList<>();
        result.add(anchor.get());
        streamTrustedEvidenceAfter(marketId, depth, anchor.get().sequenceId(), anchor.get().sequenceOrdinal(), cutoff,
                Integer.MAX_VALUE, result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<OrderBookEvidence> findTrustedReplayBase(UUID marketId, int depth, Instant cutoff) {
        return findUniqueEligibleSequence(marketId, depth, cutoff)
                .flatMap(sequenceId -> findLatestTrustedSnapshot(marketId, depth, sequenceId, cutoff));
    }

    @Transactional(readOnly = true)
    public int streamTrustedEvidenceAfter(UUID marketId, int depth, UUID sequenceId, long anchorOrdinal,
                                          Instant cutoff, int batchSize, Consumer<OrderBookEvidence> consumer) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is required");
        }
        int streamed = 0;
        long lastOrdinal = anchorOrdinal;
        while (true) {
            List<OrderBookEvidence> batch = findTrustedEvidenceAfter(
                    marketId, depth, sequenceId, lastOrdinal, cutoff, batchSize);
            if (batch.isEmpty()) {
                return streamed;
            }
            for (OrderBookEvidence evidence : batch) {
                consumer.accept(evidence);
                streamed++;
                lastOrdinal = evidence.sequenceOrdinal();
            }
            if (batch.size() < batchSize) {
                return streamed;
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isCurrentEligibleSequence(UUID sequenceId, UUID marketId, int depth, Instant cutoff) {
        return findUniqueEligibleSequence(marketId, depth, cutoff)
                .filter(sequenceId::equals)
                .isPresent();
    }

    private Optional<UUID> findUniqueEligibleSequence(UUID marketId, int depth, Instant cutoff) {
        List<UUID> candidates = jdbc.queryForList("""
                        SELECT sequence.sequence_id
                        FROM historical_order_book_sequence sequence
                        WHERE sequence.market_id = ? AND sequence.depth = ?
                          AND EXISTS (
                              SELECT 1
                              FROM historical_order_book_evidence snapshot
                              WHERE snapshot.market_id = sequence.market_id
                                AND snapshot.depth = sequence.depth
                                AND snapshot.sequence_id = sequence.sequence_id
                                AND snapshot.trusted = true
                                AND snapshot.evidence_type = 'SNAPSHOT'
                                AND snapshot.known_at <= ?
                          )
                          AND (sequence.status = 'ACTIVE'
                               OR (sequence.status = 'SEALED' AND EXISTS (
                                   SELECT 1
                                   FROM historical_order_book_evidence latest
                                   WHERE latest.market_id = sequence.market_id
                                     AND latest.depth = sequence.depth
                                     AND latest.sequence_id = sequence.sequence_id
                                     AND latest.trusted = true
                                     AND latest.known_at >= ?
                               )))
                          """ + lockingClause() + "\n LIMIT 2", UUID.class, marketId, depth,
                Timestamp.from(cutoff), Timestamp.from(cutoff));
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private String lockingClause() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2") ? "" : " FOR SHARE";
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    public void openSegment(OrderBookEvidence evidence, Instant createdAt) {
        UUID segmentId = UUID.nameUUIDFromBytes(("order-book-segment-v1:" + evidence.sequenceId() + ":"
                        + evidence.sequenceOrdinal()).getBytes(StandardCharsets.UTF_8));
        String columns = "(segment_id, market_id, provider, provider_symbol, depth, sequence_id, "
                + "start_ordinal, start_known_at, status, classification, created_at)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 'CURRENT', ?)";
        String sql = isH2()
                ? "MERGE INTO historical_order_book_evidence_segment " + columns + " KEY(sequence_id, start_ordinal) " + values
                : "INSERT INTO historical_order_book_evidence_segment " + columns + " " + values
                + " ON CONFLICT (sequence_id, start_ordinal) DO NOTHING";
        jdbc.update(sql,
                segmentId, evidence.marketId(), evidence.provider().name(), evidence.providerSymbol(),
                evidence.depth(), evidence.sequenceId(), evidence.sequenceOrdinal(),
                Timestamp.from(evidence.knownAt()), Timestamp.from(createdAt));
    }

    private void rotateSegment(OrderBookEvidence checkpoint, Instant closedAt) {
        if (checkpoint.sequenceOrdinal() == 0) {
            openSegment(checkpoint, closedAt);
            return;
        }
        jdbc.update("""
                UPDATE historical_order_book_evidence_segment
                   SET end_ordinal = ?, end_known_at = ?, successor_evidence_id = ?,
                       successor_origin = ?, status = 'CLOSED', closed_at = ?
                 WHERE sequence_id = ? AND status = 'OPEN' AND start_ordinal < ?
                """, checkpoint.sequenceOrdinal() - 1, Timestamp.from(checkpoint.knownAt()),
                checkpoint.evidenceId(), checkpoint.origin().name(), Timestamp.from(closedAt),
                checkpoint.sequenceId(), checkpoint.sequenceOrdinal());
        openSegment(checkpoint, closedAt);
    }

    private void closeSegmentsForSequences(UUID marketId, int depth, Instant closedAt) {
        jdbc.update("""
                UPDATE historical_order_book_evidence_segment segment
                   SET end_ordinal = latest.last_ordinal,
                       end_known_at = latest.last_known_at,
                       status = 'CLOSED', closed_at = ?
                  FROM (
                      SELECT sequence_id, MAX(sequence_ordinal) AS last_ordinal,
                             MAX(known_at) AS last_known_at
                        FROM historical_order_book_evidence
                       WHERE market_id = ? AND depth = ?
                       GROUP BY sequence_id
                  ) latest
                 WHERE segment.sequence_id = latest.sequence_id
                   AND segment.status = 'OPEN'
                """, Timestamp.from(closedAt), marketId, depth);
    }

    private void closeSegmentsForSequence(UUID sequenceId, Instant closedAt) {
        jdbc.update("""
                UPDATE historical_order_book_evidence_segment segment
                   SET end_ordinal = latest.last_ordinal,
                       end_known_at = latest.last_known_at,
                       status = 'CLOSED', closed_at = ?
                  FROM (
                      SELECT MAX(sequence_ordinal) AS last_ordinal,
                             MAX(known_at) AS last_known_at
                        FROM historical_order_book_evidence
                       WHERE sequence_id = ?
                  ) latest
                 WHERE segment.sequence_id = ?
                   AND segment.status = 'OPEN'
                   AND latest.last_ordinal IS NOT NULL
                """, Timestamp.from(closedAt), sequenceId, sequenceId);
    }

    public List<OrderBookEvidenceSegment> findSegments(int limit) {
        return jdbc.query("""
                SELECT segment_id, market_id, provider, provider_symbol, depth, sequence_id,
                       start_ordinal, end_ordinal, start_known_at, end_known_at,
                       successor_evidence_id, successor_origin, status, classification,
                       created_at, closed_at
                  FROM historical_order_book_evidence_segment
                 ORDER BY sequence_id, start_ordinal
                 LIMIT ?
                """, (result, row) -> new OrderBookEvidenceSegment(
                result.getObject("segment_id", UUID.class), result.getObject("market_id", UUID.class),
                result.getString("provider"), result.getString("provider_symbol"), result.getInt("depth"),
                result.getObject("sequence_id", UUID.class), result.getLong("start_ordinal"),
                result.getObject("end_ordinal", Long.class), result.getTimestamp("start_known_at").toInstant(),
                toInstant(result.getTimestamp("end_known_at")), result.getObject("successor_evidence_id", UUID.class),
                result.getString("successor_origin") == null ? null
                        : OrderBookEvidenceOrigin.valueOf(result.getString("successor_origin")),
                OrderBookEvidenceSegmentStatus.valueOf(result.getString("status")),
                OrderBookEvidenceSegmentClassification.valueOf(result.getString("classification")),
                result.getTimestamp("created_at").toInstant(), toInstant(result.getTimestamp("closed_at"))), limit);
    }

    public void markLegacyUnknown(UUID segmentId) {
        jdbc.update("""
                UPDATE historical_order_book_evidence_segment
                   SET classification = 'LEGACY_UNKNOWN'
                 WHERE segment_id = ?
                """, segmentId);
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Optional<OrderBookEvidence> findLatestTrustedSnapshot(
            UUID marketId, int depth, UUID sequenceId, Instant cutoff) {
        return jdbc.query("""
                        SELECT evidence_id, market_id, provider, symbol, provider_symbol, depth,
                               evidence_type, origin, source_start_ordinal, source_end_ordinal,
                               sequence_id, sequence_ordinal, provider_sequence, occurred_at, known_at,
                               bids_payload, asks_payload, checksum, normalization_version, content_fingerprint
                        FROM historical_order_book_evidence
                        WHERE market_id = ? AND depth = ? AND sequence_id = ?
                          AND trusted = true AND evidence_type = 'SNAPSHOT' AND known_at <= ?
                        ORDER BY known_at DESC, sequence_ordinal DESC
                        LIMIT 1
                        """, this::map, marketId, depth, sequenceId, Timestamp.from(cutoff))
                .stream().findFirst();
    }

    private List<OrderBookEvidence> findTrustedEvidenceAfter(
            UUID marketId, int depth, UUID sequenceId, long anchorOrdinal, Instant cutoff, int batchSize) {
        return jdbc.query("""
                        SELECT evidence_id, market_id, provider, symbol, provider_symbol, depth,
                               evidence_type, origin, source_start_ordinal, source_end_ordinal,
                               sequence_id, sequence_ordinal, provider_sequence, occurred_at, known_at,
                               bids_payload, asks_payload, checksum, normalization_version, content_fingerprint
                        FROM historical_order_book_evidence
                        WHERE market_id = ? AND depth = ? AND sequence_id = ?
                          AND trusted = true AND sequence_ordinal > ? AND known_at <= ?
                        ORDER BY sequence_ordinal
                        LIMIT ?
                        """, this::map, marketId, depth, sequenceId, anchorOrdinal, Timestamp.from(cutoff), batchSize);
    }

    private OrderBookEvidence map(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        return new OrderBookEvidence(
                result.getObject("evidence_id", UUID.class),
                result.getObject("market_id", UUID.class),
                MarketProvider.valueOf(result.getString("provider")),
                result.getString("symbol"), result.getString("provider_symbol"),
                result.getInt("depth"),
                OrderBookEvidenceType.valueOf(result.getString("evidence_type")),
                OrderBookEvidenceOrigin.valueOf(result.getString("origin")),
                result.getObject("source_start_ordinal", Long.class),
                result.getObject("source_end_ordinal", Long.class),
                result.getObject("sequence_id", UUID.class), result.getLong("sequence_ordinal"),
                result.getObject("provider_sequence", Long.class),
                result.getTimestamp("occurred_at").toInstant(),
                result.getTimestamp("known_at").toInstant(),
                OrderBookEvidencePayload.decode(result.getString("bids_payload")),
                OrderBookEvidencePayload.decode(result.getString("asks_payload")),
                result.getObject("checksum", Long.class),
                result.getString("normalization_version"),
                result.getString("content_fingerprint"));
    }
}
