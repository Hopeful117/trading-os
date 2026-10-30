package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookDependencyState;
import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookRetentionHold;
import com.hope.trading.market_data.model.OrderBookRetirementCandidate;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.model.OrderBookSequenceStatus;
import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

@Repository
public class OrderBookRetentionRepository {
    private static final Logger log = LoggerFactory.getLogger(OrderBookRetentionRepository.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final OrderBookRetentionProperties properties;

    public OrderBookRetentionRepository(JdbcTemplate jdbc, DataSource dataSource,
                                        OrderBookRetentionProperties properties) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Transactional
    public boolean saveArtifact(OrderBookReplayArtifact artifact) {
        if (isH2() && existsArtifact(artifact.semanticIdentity(), artifact.artifactFingerprint())) {
            return false;
        }
        return jdbc.update(artifactInsertSql(),
                artifact.artifactId(), artifact.semanticIdentity(), artifact.artifactFingerprint(),
                artifact.marketId(), artifact.depth(), artifact.sequenceId(),
                Timestamp.from(artifact.evaluationCutoff()), timestamp(artifact.latestOccurredAt()),
                timestamp(artifact.latestKnownAt()), artifact.sourceStartOrdinal(), artifact.sourceEndOrdinal(),
                artifact.provider(), artifact.providerSymbol(), artifact.normalizationVersion(),
                artifact.artifactSchemaVersion(), artifact.sourceEvidenceFingerprint(), artifact.sourceTrustStatus(),
                artifact.sourceEvidenceOrigin().name(),
                OrderBookEvidencePayload.encodeStrings(artifact.sourceEvidenceFingerprints()),
                OrderBookEvidencePayload.encode(artifact.bids()),
                OrderBookEvidencePayload.encode(artifact.asks()), artifact.promotionReason(),
                Timestamp.from(artifact.createdAt())) == 1;
    }

    @Transactional(readOnly = true)
    public boolean matchesPersistedEvidence(OrderBookReplayResult replay) {
        return matchesPersistedEvidence(replay, true);
    }

    @Transactional(readOnly = true)
    public boolean matchesPersistedProvenance(OrderBookReplayResult replay) {
        return matchesPersistedEvidence(replay, false);
    }

    private boolean matchesPersistedEvidence(OrderBookReplayResult replay, boolean verifyPayload) {
        List<PersistedEvidence> evidence = jdbc.query("""
                SELECT market_id, provider, provider_symbol, depth, sequence_id, sequence_ordinal,
                       occurred_at, known_at, normalization_version, trusted, content_fingerprint,
                       evidence_type, origin, bids_payload, asks_payload
                 FROM historical_order_book_evidence
                 WHERE sequence_id = ?
                   AND ((sequence_ordinal = ? AND evidence_type = 'SNAPSHOT')
                        OR (sequence_ordinal > ? AND sequence_ordinal <= ? AND evidence_type = 'DELTA'))
                   AND known_at <= ?
                 ORDER BY sequence_ordinal
                 """, (result, row) -> new PersistedEvidence(result.getObject("market_id", UUID.class),
                        result.getString("provider"), result.getString("provider_symbol"),
                        result.getInt("depth"), result.getObject("sequence_id", UUID.class),
                        result.getLong("sequence_ordinal"), result.getTimestamp("occurred_at").toInstant(),
                        result.getTimestamp("known_at").toInstant(), result.getString("normalization_version"),
                         result.getBoolean("trusted"), result.getString("content_fingerprint"),
                          result.getString("evidence_type"),
                          com.hope.trading.market_data.model.OrderBookEvidenceOrigin.valueOf(result.getString("origin")),
                          OrderBookEvidencePayload.decode(result.getString("bids_payload")),
                         OrderBookEvidencePayload.decode(result.getString("asks_payload"))), replay.sequenceId(),
                 replay.sourceStartOrdinal(), replay.sourceStartOrdinal(), replay.sourceEndOrdinal(),
                 Timestamp.from(replay.evaluationCutoff()));
        List<String> fingerprints = evidence.stream().map(PersistedEvidence::fingerprint).toList();
        boolean contiguous = evidence.stream().findFirst()
                .map(first -> evidence.size() == replay.sourceEndOrdinal() - replay.sourceStartOrdinal() + 1
                        && evidence.getLast().ordinal() == replay.sourceEndOrdinal()
                        && java.util.stream.IntStream.range(0, evidence.size())
                        .allMatch(index -> evidence.get(index).ordinal() == replay.sourceStartOrdinal() + index))
                .orElse(false);
        java.util.Set<com.hope.trading.market_data.model.OrderBookEvidenceOrigin> origins = evidence.stream()
                .map(PersistedEvidence::origin)
                .collect(java.util.stream.Collectors.toSet());
        boolean originComposition = replay.sourceEvidenceOrigin()
                == com.hope.trading.market_data.model.OrderBookEvidenceOrigin.MIXED
                ? origins.size() > 1
                : origins.size() == 1 && origins.contains(replay.sourceEvidenceOrigin());
        boolean metadata = evidence.stream().allMatch(item -> item.trusted()
                && replay.marketId().equals(item.marketId())
                && replay.depth() == item.depth()
                && replay.sequenceId().equals(item.sequenceId())
                && java.util.Objects.equals(replay.provider(), item.provider())
                && java.util.Objects.equals(replay.providerSymbol(), item.providerSymbol())
                && replay.normalizationVersion().equals(item.normalizationVersion())
                && !item.knownAt().isAfter(replay.evaluationCutoff())
                && !item.occurredAt().isAfter(replay.evaluationCutoff()));
        boolean latest = evidence.stream().findFirst().map(ignored ->
                replay.latestOccurredAt().equals(evidence.getLast().occurredAt())
                        && replay.latestKnownAt().equals(evidence.getLast().knownAt())).orElse(false);
        boolean payload = !verifyPayload || canonicalPayloadMatches(evidence, replay.depth(), replay.bids(), replay.asks());
        boolean fingerprintsMatch = fingerprints.equals(replay.sourceEvidenceFingerprints());
        boolean aggregateFingerprintMatch = fingerprint(fingerprints).equals(replay.sourceEvidenceFingerprint());
        boolean valid = fingerprintsMatch && aggregateFingerprintMatch && contiguous && metadata
                && originComposition && latest && payload;
        if (!valid) {
            log.warn("Replay evidence mismatch sequence={} start={} end={} rows={} fingerprints={} aggregate={} contiguous={} metadata={} latest={} payload={}",
                    replay.sequenceId(), replay.sourceStartOrdinal(), replay.sourceEndOrdinal(), evidence.size(),
                    fingerprintsMatch, aggregateFingerprintMatch, contiguous, metadata, latest, payload);
        }
        return valid;
    }

    @Transactional(readOnly = true)
    public boolean matchesArtifactFingerprint(OrderBookReplayArtifact artifact) {
        return ("order-book-replay-artifact-v2".equals(artifact.artifactSchemaVersion())
                || "order-book-replay-artifact-v3".equals(artifact.artifactSchemaVersion()))
                && OrderBookReplayArtifactFingerprint.matches(artifact, properties.requireArtifactSigningKey());
    }

    @Transactional(readOnly = true)
    public Optional<OrderBookReplayArtifact> findArtifact(String semanticIdentity) {
        return findArtifact(semanticIdentity, false);
    }

    @Transactional(readOnly = true)
    public Optional<OrderBookReplayArtifact> findArtifactForPromotion(String semanticIdentity) {
        return findArtifact(semanticIdentity, true);
    }

    private Optional<OrderBookReplayArtifact> findArtifact(String semanticIdentity, boolean includeInvalidated) {
        List<OrderBookReplayArtifact> artifacts = jdbc.query("""
                SELECT artifact.artifact_id, artifact.semantic_identity, artifact.artifact_fingerprint,
                       artifact.market_id, artifact.depth, artifact.sequence_id,
                       artifact.evaluation_cutoff, artifact.latest_occurred_at, artifact.latest_known_at,
                       artifact.source_start_ordinal, artifact.source_end_ordinal, artifact.provider,
                       artifact.provider_symbol, artifact.normalization_version, artifact.artifact_schema_version,
                        artifact.source_evidence_fingerprint, artifact.source_trust_status, artifact.source_origin,
                       artifact.source_evidence_fingerprints_payload, artifact.bids_payload, artifact.asks_payload,
                       artifact.promotion_reason, artifact.created_at
                   FROM historical_order_book_replay_artifact artifact
                   JOIN historical_order_book_sequence sequence
                     ON sequence.sequence_id = artifact.sequence_id
                  WHERE artifact.semantic_identity = ?
                 """ + (includeInvalidated ? "" : " AND sequence.status <> 'INVALIDATED'\n"
                         + " AND EXISTS (SELECT 1 FROM historical_order_book_evidence_dependency dependency\n"
                         + "             WHERE dependency.artifact_id = artifact.artifact_id\n"
                         + "               AND dependency.state = 'PROTECTED')\n"),
                (result, row) -> new OrderBookReplayArtifact(
                result.getObject("artifact_id", UUID.class), result.getString("semantic_identity"),
                result.getString("artifact_fingerprint"), result.getObject("market_id", UUID.class),
                result.getInt("depth"), result.getObject("sequence_id", UUID.class),
                result.getTimestamp("evaluation_cutoff").toInstant(),
                instant(result.getTimestamp("latest_occurred_at")), instant(result.getTimestamp("latest_known_at")),
                result.getObject("source_start_ordinal", Long.class), result.getObject("source_end_ordinal", Long.class),
                result.getString("provider"), result.getString("provider_symbol"),
                 result.getString("normalization_version"), result.getString("artifact_schema_version"),
                  result.getString("source_evidence_fingerprint"), result.getString("source_trust_status"),
                 sourceFingerprints(result.getString("source_evidence_fingerprints_payload"),
                 result.getString("source_evidence_fingerprint")),
                 OrderBookEvidencePayload.decode(result.getString("bids_payload")),
                OrderBookEvidencePayload.decode(result.getString("asks_payload")),
                 result.getString("promotion_reason"), result.getTimestamp("created_at").toInstant(),
                 com.hope.trading.market_data.model.OrderBookEvidenceOrigin.valueOf(result.getString("source_origin"))),
                semanticIdentity);
        return artifacts.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public boolean isInvalidatedSequence(UUID sequenceId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) > 0
                  FROM historical_order_book_sequence
                 WHERE sequence_id = ? AND status = 'INVALIDATED'
                """, Boolean.class, sequenceId);
    }

    @Transactional
    public boolean saveDependency(OrderBookEvidenceDependency dependency) {
        lockProtectionTarget(dependency.artifactId(), dependency.sequenceId());
        if (isH2() && existsDependency(dependency.dependencyKey())) {
            return false;
        }
        return jdbc.update(dependencyInsertSql(),
                dependency.dependencyId(), dependency.dependencyKey(), dependency.consumerType(),
                dependency.consumerIdentity(), dependency.artifactId(), dependency.sequenceId(),
                dependency.state().name(), dependency.reason(), Timestamp.from(dependency.createdAt()),
                timestamp(dependency.releasedAt())) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<OrderBookEvidenceDependency> findDependency(String dependencyKey) {
        return jdbc.query("""
                SELECT dependency_id, dependency_key, consumer_type, consumer_identity, artifact_id, sequence_id,
                       state, reason, created_at, released_at
                  FROM historical_order_book_evidence_dependency
                 WHERE dependency_key = ?
                """, (result, row) -> new OrderBookEvidenceDependency(
                result.getObject("dependency_id", UUID.class), result.getString("dependency_key"),
                result.getString("consumer_type"), result.getString("consumer_identity"),
                result.getObject("artifact_id", UUID.class), result.getObject("sequence_id", UUID.class),
                OrderBookDependencyState.valueOf(result.getString("state")), result.getString("reason"),
                result.getTimestamp("created_at").toInstant(), instant(result.getTimestamp("released_at"))),
                dependencyKey).stream().findFirst();
    }

    @Transactional
    public boolean releaseDependency(String dependencyKey, String reason, Instant releasedAt) {
        return jdbc.update("""
                UPDATE historical_order_book_evidence_dependency
                   SET state = 'RELEASED', reason = ?, released_at = ?
                 WHERE dependency_key = ? AND state <> 'RELEASED'
                   AND state IN ('PROTECTED', 'HOLD')
                """, reason, Timestamp.from(releasedAt), dependencyKey) == 1;
    }

    @Transactional
    public boolean saveHold(OrderBookRetentionHold hold) {
        lockProtectionTarget(hold.artifactId(), hold.sequenceId());
        if (isH2() && existsHold(hold.holdKey())) {
            return false;
        }
        return jdbc.update(holdInsertSql(), hold.holdId(), hold.holdKey(), hold.artifactId(), hold.sequenceId(),
                hold.reason(), Timestamp.from(hold.createdAt()), timestamp(hold.releasedAt())) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<OrderBookRetentionHold> findHold(String holdKey) {
        return jdbc.query("""
                SELECT hold_id, hold_key, artifact_id, sequence_id, reason, created_at, released_at
                  FROM historical_order_book_retention_hold
                 WHERE hold_key = ?
                """, (result, row) -> new OrderBookRetentionHold(
                result.getObject("hold_id", UUID.class), result.getString("hold_key"),
                result.getObject("artifact_id", UUID.class), result.getObject("sequence_id", UUID.class),
                result.getString("reason"), result.getTimestamp("created_at").toInstant(),
                instant(result.getTimestamp("released_at"))), holdKey).stream().findFirst();
    }

    @Transactional
    public boolean releaseHold(String holdKey, Instant releasedAt) {
        return jdbc.update("""
                UPDATE historical_order_book_retention_hold
                   SET released_at = ?
                 WHERE hold_key = ? AND released_at IS NULL
                """, Timestamp.from(releasedAt), holdKey) == 1;
    }

    @Transactional(readOnly = true)
    public List<OrderBookRetirementCandidate> findRetirementCandidates(Instant invalidatedBefore) {
        return jdbc.query("""
                SELECT sequence.sequence_id, sequence.market_id, sequence.depth, sequence.status,
                       COUNT(evidence.evidence_id) AS evidence_count,
                       MIN(evidence.sequence_ordinal) AS first_ordinal,
                       MAX(evidence.sequence_ordinal) AS last_ordinal,
                       MIN(evidence.known_at) AS first_known_at,
                       MAX(evidence.known_at) AS last_known_at,
                       sequence.invalidated_at
                  FROM historical_order_book_sequence sequence
                  JOIN historical_order_book_evidence evidence
                    ON evidence.sequence_id = sequence.sequence_id
                   WHERE (sequence.status = 'SEALED'
                          OR (sequence.status = 'INVALIDATED'
                              AND sequence.invalidated_at IS NOT NULL
                              AND sequence.invalidated_at <= ?))
                   AND NOT EXISTS (
                       SELECT 1
                         FROM historical_order_book_evidence_dependency dependency
                        WHERE dependency.state <> 'RELEASED'
                          AND (dependency.sequence_id = sequence.sequence_id
                               OR EXISTS (
                                   SELECT 1
                                     FROM historical_order_book_replay_artifact artifact
                                    WHERE artifact.artifact_id = dependency.artifact_id
                                      AND artifact.sequence_id = sequence.sequence_id)))
                    AND NOT EXISTS (
                        SELECT 1
                          FROM historical_order_book_retention_hold hold
                        WHERE hold.released_at IS NULL
                          AND (hold.sequence_id = sequence.sequence_id
                               OR EXISTS (
                                   SELECT 1
                                     FROM historical_order_book_replay_artifact artifact
                                     WHERE artifact.artifact_id = hold.artifact_id
                                       AND artifact.sequence_id = sequence.sequence_id)))
                    AND EXISTS (
                         SELECT 1
                          FROM historical_order_book_evidence_dependency released_dependency
                         WHERE released_dependency.state = 'RELEASED'
                           AND (released_dependency.sequence_id = sequence.sequence_id
                                OR EXISTS (
                                    SELECT 1
                                      FROM historical_order_book_replay_artifact artifact
                                     WHERE artifact.artifact_id = released_dependency.artifact_id
                                      AND artifact.sequence_id = sequence.sequence_id)))
                    AND NOT EXISTS (
                         SELECT 1
                           FROM historical_order_book_replay_artifact unverified_artifact
                          WHERE unverified_artifact.sequence_id = sequence.sequence_id
                            AND unverified_artifact.source_trust_status <> 'TRUSTED')
                  GROUP BY sequence.sequence_id, sequence.market_id, sequence.depth, sequence.status,
                           sequence.invalidated_at
                 ORDER BY MIN(evidence.known_at), sequence.sequence_id
                """, (result, row) -> new OrderBookRetirementCandidate(
                result.getObject("sequence_id", java.util.UUID.class),
                result.getObject("market_id", java.util.UUID.class),
                result.getInt("depth"),
                OrderBookSequenceStatus.valueOf(result.getString("status")),
                result.getLong("evidence_count"), result.getLong("first_ordinal"),
                 result.getLong("last_ordinal"), result.getTimestamp("first_known_at").toInstant(),
                  result.getTimestamp("last_known_at").toInstant(), instant(result.getTimestamp("invalidated_at"))),
                  Timestamp.from(invalidatedBefore));
    }

    @Transactional(readOnly = true)
    public String retirementDetails(OrderBookRetirementCandidate candidate) {
        List<UUID> artifacts = jdbc.query("""
                SELECT artifact_id FROM historical_order_book_replay_artifact
                 WHERE sequence_id = ? ORDER BY artifact_id
                """, (result, row) -> result.getObject("artifact_id", UUID.class), candidate.sequenceId());
        List<UUID> dependencies = jdbc.query("""
                SELECT dependency_id FROM historical_order_book_evidence_dependency dependency
                 WHERE dependency.sequence_id = ? OR EXISTS (
                       SELECT 1 FROM historical_order_book_replay_artifact artifact
                        WHERE artifact.artifact_id = dependency.artifact_id AND artifact.sequence_id = ?)
                 ORDER BY dependency_id
                """, (result, row) -> result.getObject("dependency_id", UUID.class), candidate.sequenceId(),
                candidate.sequenceId());
        List<UUID> holds = jdbc.query("""
                SELECT hold_id FROM historical_order_book_retention_hold hold
                 WHERE hold.sequence_id = ? OR EXISTS (
                       SELECT 1 FROM historical_order_book_replay_artifact artifact
                        WHERE artifact.artifact_id = hold.artifact_id AND artifact.sequence_id = ?)
                 ORDER BY hold_id
                """, (result, row) -> result.getObject("hold_id", UUID.class), candidate.sequenceId(),
                candidate.sequenceId());
        return candidate.sequenceId() + ":" + candidate.firstOrdinal() + "-" + candidate.lastOrdinal()
                + ";artifacts=" + artifacts + ";dependencies=" + dependencies + ";holds=" + holds;
    }

    @Transactional
    public boolean recordDryRun(String operationFingerprint, String details, Instant selectedAt,
                                java.util.UUID operationId) {
        if (isH2() && existsOperation(operationFingerprint)) {
            return false;
        }
        return jdbc.update(operationInsertSql(), operationId, operationFingerprint, "DRY_RUN", "SELECTED",
                Timestamp.from(selectedAt), details) == 1;
    }

    @Transactional
    public boolean recordMutation(String operationFingerprint, String details, Instant selectedAt,
                                  UUID operationId) {
        if (isH2() && existsOperation(operationFingerprint)) {
            return false;
        }
        return jdbc.update(operationInsertSql(), operationId, operationFingerprint, "MUTATION", "SELECTED",
                Timestamp.from(selectedAt), details) == 1;
    }

    @Transactional(readOnly = true)
    public boolean matchesDryRun(String operationFingerprint, String details) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM historical_order_book_retirement_operation
                 WHERE operation_fingerprint = ? AND mode = 'DRY_RUN'
                   AND status = 'SELECTED' AND details LIKE ?
                """, Long.class, operationFingerprint, "%" + details + "%") > 0;
    }

    @Transactional
    public void completeOperation(String operationFingerprint, Instant completedAt) {
        completeOperation(operationFingerprint, completedAt, 0);
    }

    @Transactional
    public void completeOperation(String operationFingerprint, Instant completedAt, int affectedCount) {
        jdbc.update("""
                UPDATE historical_order_book_retirement_operation
                   SET status = 'COMPLETED', completed_at = ?, affected_count = affected_count + ?, outcome = 'COMPLETED'
                 WHERE operation_fingerprint = ? AND status = 'SELECTED'
                """, Timestamp.from(completedAt), affectedCount, operationFingerprint);
    }

    @Transactional
    public void recordPartialOperation(String operationFingerprint, int affectedCount) {
        jdbc.update("""
                UPDATE historical_order_book_retirement_operation
                   SET affected_count = affected_count + ?, outcome = 'PARTIAL'
                 WHERE operation_fingerprint = ? AND status = 'SELECTED'
                """, affectedCount, operationFingerprint);
    }

    @Transactional
    public int retireEvidence(OrderBookRetirementCandidate candidate, int batchSize, Instant invalidatedBefore) {
        lockRetirementTarget(candidate.sequenceId());
        return jdbc.update("""
                DELETE FROM historical_order_book_evidence
                 WHERE evidence_id IN (
                     SELECT evidence.evidence_id
                       FROM historical_order_book_evidence evidence
                      WHERE evidence.sequence_id = ?
                        AND evidence.sequence_ordinal BETWEEN ? AND ?
                        AND EXISTS (
                         SELECT 1
                          FROM historical_order_book_sequence sequence
                         WHERE sequence.sequence_id = evidence.sequence_id
                           AND (sequence.status = 'SEALED' OR
                                (sequence.status = 'INVALIDATED' AND sequence.invalidated_at IS NOT NULL
                                 AND sequence.invalidated_at <= ?))
                   )
                        AND NOT EXISTS (
                       SELECT 1
                         FROM historical_order_book_evidence_dependency dependency
                        WHERE dependency.state <> 'RELEASED'
                          AND (dependency.sequence_id = evidence.sequence_id
                               OR EXISTS (
                                   SELECT 1
                                     FROM historical_order_book_replay_artifact artifact
                                    WHERE artifact.artifact_id = dependency.artifact_id
                                      AND artifact.sequence_id = evidence.sequence_id)))
                        AND NOT EXISTS (
                       SELECT 1
                         FROM historical_order_book_retention_hold hold
                        WHERE hold.released_at IS NULL
                          AND (hold.sequence_id = evidence.sequence_id
                               OR EXISTS (
                                   SELECT 1
                                     FROM historical_order_book_replay_artifact artifact
                                     WHERE artifact.artifact_id = hold.artifact_id
                                       AND artifact.sequence_id = evidence.sequence_id)))
                        AND EXISTS (
                        SELECT 1
                          FROM historical_order_book_evidence_dependency released_dependency
                         WHERE released_dependency.state = 'RELEASED'
                           AND (released_dependency.sequence_id = evidence.sequence_id
                                OR EXISTS (
                                    SELECT 1
                                      FROM historical_order_book_replay_artifact artifact
                                     WHERE artifact.artifact_id = released_dependency.artifact_id
                                       AND artifact.sequence_id = evidence.sequence_id)))
                        AND NOT EXISTS (
                         SELECT 1
                           FROM historical_order_book_replay_artifact unverified_artifact
                          WHERE unverified_artifact.sequence_id = evidence.sequence_id
                            AND unverified_artifact.source_trust_status <> 'TRUSTED')
                      ORDER BY evidence.sequence_ordinal
                      LIMIT ?
                 )
                 """, candidate.sequenceId(), candidate.firstOrdinal(), candidate.lastOrdinal(), Timestamp.from(invalidatedBefore),
                batchSize);
    }

    private void lockProtectionTarget(UUID artifactId, UUID sequenceId) {
        UUID targetSequenceId = resolveTargetSequence(artifactId, sequenceId);
        lockRetirementTarget(targetSequenceId);
        if (jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM historical_order_book_evidence
                 WHERE sequence_id = ?
                """, Long.class, targetSequenceId) == 0) {
            throw new IllegalStateException("cannot protect a sequence without retained evidence");
        }
    }

    private UUID resolveTargetSequence(UUID artifactId, UUID sequenceId) {
        UUID artifactSequence = artifactId == null ? null : jdbc.queryForObject("""
                SELECT sequence_id
                  FROM historical_order_book_replay_artifact
                 WHERE artifact_id = ?
                """, UUID.class, artifactId);
        if (sequenceId != null && artifactSequence != null && !sequenceId.equals(artifactSequence)) {
            throw new IllegalArgumentException("dependency target sequence does not match artifact sequence");
        }
        UUID target = sequenceId == null ? artifactSequence : sequenceId;
        if (target == null) {
            throw new IllegalArgumentException("a dependency or hold target is required");
        }
        return target;
    }

    private void lockRetirementTarget(UUID sequenceId) {
        jdbc.queryForObject("""
                SELECT sequence_id
                  FROM historical_order_book_sequence
                 WHERE sequence_id = ?
                 FOR UPDATE
                """, UUID.class, sequenceId);
    }

    private String artifactInsertSql() {
        String columns = "(artifact_id, semantic_identity, artifact_fingerprint, market_id, depth, sequence_id, "
                + "evaluation_cutoff, latest_occurred_at, latest_known_at, source_start_ordinal, source_end_ordinal, "
                 + "provider, provider_symbol, normalization_version, artifact_schema_version, source_evidence_fingerprint, "
                 + "source_trust_status, source_origin, source_evidence_fingerprints_payload, bids_payload, asks_payload, promotion_reason, created_at)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "INSERT INTO historical_order_book_replay_artifact " + columns + " " + values;
        }
        return "INSERT INTO historical_order_book_replay_artifact " + columns + " " + values
                + " ON CONFLICT (semantic_identity) DO NOTHING";
    }

    private String dependencyInsertSql() {
        String columns = "(dependency_id, dependency_key, consumer_type, consumer_identity, artifact_id, sequence_id, "
                + "state, reason, created_at, released_at)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "INSERT INTO historical_order_book_evidence_dependency " + columns + " " + values;
        }
        return "INSERT INTO historical_order_book_evidence_dependency " + columns + " " + values
                + " ON CONFLICT (dependency_key) DO NOTHING";
    }

    private String holdInsertSql() {
        String columns = "(hold_id, hold_key, artifact_id, sequence_id, reason, created_at, released_at)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "INSERT INTO historical_order_book_retention_hold " + columns + " " + values;
        }
        return "INSERT INTO historical_order_book_retention_hold " + columns + " " + values
                + " ON CONFLICT (hold_key) DO NOTHING";
    }

    private String operationInsertSql() {
        String columns = "(operation_id, operation_fingerprint, mode, status, selected_at, details)";
        String values = "VALUES (?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "INSERT INTO historical_order_book_retirement_operation " + columns + " " + values;
        }
        return "INSERT INTO historical_order_book_retirement_operation " + columns + " " + values
                + " ON CONFLICT (operation_fingerprint) DO NOTHING";
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private List<String> sourceFingerprints(String payload, String aggregateFingerprint) {
        List<String> fingerprints = OrderBookEvidencePayload.decodeStrings(payload);
        return fingerprints.isEmpty() ? List.of(aggregateFingerprint) : fingerprints;
    }

    private String fingerprint(List<String> values) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", values).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private boolean canonicalPayloadMatches(List<PersistedEvidence> evidence, int depth,
                                            List<com.hope.trading.market_data.model.OrderBookLevel> expectedBids,
                                            List<com.hope.trading.market_data.model.OrderBookLevel> expectedAsks) {
        if (evidence.isEmpty() || !"SNAPSHOT".equals(evidence.getFirst().evidenceType())) {
            return false;
        }
        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(java.util.Comparator.reverseOrder());
        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        apply(bids, evidence.getFirst().bids());
        apply(asks, evidence.getFirst().asks());
        for (PersistedEvidence item : evidence.subList(1, evidence.size())) {
            if (!"DELTA".equals(item.evidenceType())) return false;
            apply(bids, item.bids());
            apply(asks, item.asks());
        }
        return limit(bids, depth).equals(expectedBids) && limit(asks, depth).equals(expectedAsks);
    }

    private void apply(NavigableMap<BigDecimal, BigDecimal> book,
                       List<com.hope.trading.market_data.model.OrderBookLevel> levels) {
        levels.forEach(level -> {
            if (level.quantity().signum() == 0) book.remove(level.price());
            else book.put(level.price(), level.quantity());
        });
    }

    private List<com.hope.trading.market_data.model.OrderBookLevel> limit(
            NavigableMap<BigDecimal, BigDecimal> book, int depth) {
        return book.entrySet().stream().limit(depth)
                .map(entry -> new com.hope.trading.market_data.model.OrderBookLevel(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean existsArtifact(String semanticIdentity, String artifactFingerprint) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_replay_artifact
                 WHERE semantic_identity = ? OR artifact_fingerprint = ?
                """, Long.class, semanticIdentity, artifactFingerprint) > 0;
    }

    private boolean existsDependency(String dependencyKey) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence_dependency WHERE dependency_key = ?
                """, Long.class, dependencyKey) > 0;
    }

    private boolean existsHold(String holdKey) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_retention_hold WHERE hold_key = ?
                """, Long.class, holdKey) > 0;
    }

    private boolean existsOperation(String operationFingerprint) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_retirement_operation
                 WHERE operation_fingerprint = ?
                """, Long.class, operationFingerprint) > 0;
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    private record PersistedEvidence(UUID marketId, String provider, String providerSymbol,
                                     int depth, UUID sequenceId, long ordinal,
                                     Instant occurredAt, Instant knownAt, String normalizationVersion,
                                      boolean trusted, String fingerprint, String evidenceType,
                                      com.hope.trading.market_data.model.OrderBookEvidenceOrigin origin,
                                      List<com.hope.trading.market_data.model.OrderBookLevel> bids,
                                     List<com.hope.trading.market_data.model.OrderBookLevel> asks) {
    }
}
