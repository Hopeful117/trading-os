package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        ensureSequence(evidence);
        return insertEvidence(evidence);
    }

    @Transactional
    public boolean appendSnapshotAsNewSequence(OrderBookEvidence evidence, Instant supersededAt) {
        if (exists(evidence.contentFingerprint())) {
            return false;
        }
        ensureSequence(evidence);
        if (!insertEvidence(evidence)) {
            throw new IllegalStateException("Unable to persist new order-book snapshot sequence");
        }
        sealOtherActiveSequences(evidence.marketId(), evidence.depth(), evidence.sequenceId(),
                "superseded by fresh provider snapshot", supersededAt);
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
        return rows == 1;
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
                        WHERE target.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1
                              FROM historical_order_book_sequence source
                              WHERE source.sequence_id = ?
                                AND source.market_id = target.market_id
                                AND source.depth = target.depth
                          )
                        """,
                Timestamp.from(invalidatedAt), reason, sequenceId);
    }

    @Transactional
    public void invalidateActiveSequences(UUID marketId, int depth, String reason, Instant invalidatedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'INVALIDATED', sealed_at = NULL, invalidated_at = ?, invalidation_reason = ?
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE'
                        """,
                Timestamp.from(invalidatedAt), reason, marketId, depth);
    }

    @Transactional
    public void sealActiveSequences(UUID marketId, int depth, Instant sealedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'SEALED', sealed_at = ?, invalidated_at = NULL, invalidation_reason = NULL
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE'
                        """,
                Timestamp.from(sealedAt), marketId, depth);
    }

    private void sealOtherActiveSequences(UUID marketId, int depth, UUID sequenceId,
                                           String ignoredReason, Instant sealedAt) {
        jdbc.update("""
                        UPDATE historical_order_book_sequence
                        SET status = 'SEALED', sealed_at = ?, invalidated_at = NULL, invalidation_reason = NULL
                        WHERE market_id = ? AND depth = ? AND status = 'ACTIVE' AND sequence_id <> ?
                        """,
                Timestamp.from(sealedAt), marketId, depth, sequenceId);
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
        return jdbc.query("""
                        WITH eligible_sequences AS (
                            SELECT sequence.sequence_id, sequence.status
                            FROM historical_order_book_sequence sequence
                            WHERE sequence.market_id = ? AND sequence.depth = ?
                              AND EXISTS (
                                   SELECT 1
                                   FROM historical_order_book_evidence snapshot
                                   WHERE snapshot.sequence_id = sequence.sequence_id
                                     AND snapshot.market_id = sequence.market_id
                                     AND snapshot.depth = sequence.depth
                                     AND snapshot.trusted = true
                                     AND snapshot.evidence_type = 'SNAPSHOT'
                                     AND snapshot.known_at <= ?
                              )
                              AND (sequence.status = 'ACTIVE'
                                   OR (sequence.status = 'SEALED' AND EXISTS (
                                       SELECT 1
                                        FROM historical_order_book_evidence latest
                                        WHERE latest.sequence_id = sequence.sequence_id
                                          AND latest.market_id = sequence.market_id
                                          AND latest.depth = sequence.depth
                                          AND latest.trusted = true
                                          AND latest.known_at >= ?
                                   )))
                        ), selected_sequence AS (
                            SELECT sequence_id
                            FROM eligible_sequences
                            WHERE (SELECT COUNT(*) FROM eligible_sequences) = 1
                        )
                        SELECT evidence.evidence_id, evidence.market_id, evidence.provider, evidence.symbol,
                               evidence.provider_symbol, evidence.depth, evidence.evidence_type, evidence.origin,
                               evidence.source_start_ordinal, evidence.source_end_ordinal,
                               evidence.sequence_id, evidence.sequence_ordinal, evidence.provider_sequence,
                               evidence.occurred_at, evidence.known_at, evidence.bids_payload, evidence.asks_payload,
                               evidence.checksum, evidence.normalization_version, evidence.content_fingerprint
                        FROM historical_order_book_evidence evidence
                        JOIN selected_sequence selected
                          ON selected.sequence_id = evidence.sequence_id
                        WHERE evidence.market_id = ? AND evidence.depth = ?
                          AND evidence.trusted = true
                          AND evidence.known_at <= ?
                        ORDER BY evidence.known_at, evidence.sequence_ordinal
                        """,
                (result, row) -> new OrderBookEvidence(
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
                        result.getString("content_fingerprint")),
                 marketId, depth, Timestamp.from(cutoff), Timestamp.from(cutoff),
                 marketId, depth, Timestamp.from(cutoff));
    }
}
