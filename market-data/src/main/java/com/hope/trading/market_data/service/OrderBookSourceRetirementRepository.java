package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalEvidenceRetirementBusinessState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementOperationalState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderBookSourceRetirementRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public OrderBookSourceRetirementRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public boolean isSourceRetired(UUID marketId, int depth) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) > 0
                  FROM historical_order_book_source_retirement_operation
                 WHERE market_id = ? AND depth = ? AND business_state = 'SOURCE_RETIRED'
                """, Boolean.class, marketId, depth);
    }

    @Transactional(readOnly = true)
    public boolean isSourceRetired(String provider, String providerSymbol, UUID marketId, int depth) {
        String lock = isH2() ? "" : " FOR SHARE";
        return !jdbc.queryForList("""
                SELECT operation_id
                  FROM historical_order_book_source_retirement_operation
                 WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?
                   AND business_state = 'SOURCE_RETIRED'
                """ + lock, UUID.class, provider, providerSymbol, marketId, depth).isEmpty();
    }

    @Transactional(readOnly = true)
    public Optional<Operation> find(UUID operationId) {
        return find(operationId, false);
    }

    @Transactional
    public Optional<Operation> findForUpdate(UUID operationId) {
        return find(operationId, true);
    }

    private Optional<Operation> find(UUID operationId, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return jdbc.query("""
                SELECT operation_id, operation_fingerprint, provider, provider_symbol, market_id, depth,
                       approval_reference, actor, business_state, operational_state,
                       preflight_fingerprint, inventory_fingerprint, dependency_fingerprint,
                       failure_reason, created_at, updated_at
                  FROM historical_order_book_source_retirement_operation
                 WHERE operation_id = ?
                """ + lock, (result, row) -> new Operation(
                result.getObject("operation_id", UUID.class),
                result.getString("operation_fingerprint"),
                new HistoricalEvidenceRetirementScope(
                        com.hope.trading.market_data.helper.MarketProvider.valueOf(result.getString("provider")),
                        result.getString("provider_symbol"), result.getObject("market_id", UUID.class),
                        result.getInt("depth")), result.getString("approval_reference"), result.getString("actor"),
                HistoricalEvidenceRetirementBusinessState.valueOf(result.getString("business_state")),
                HistoricalEvidenceRetirementOperationalState.valueOf(result.getString("operational_state")),
                result.getString("preflight_fingerprint"), result.getString("inventory_fingerprint"),
                result.getString("dependency_fingerprint"), result.getString("failure_reason"),
                result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant()),
                operationId).stream().findFirst();
    }

    @Transactional
    public boolean savePrepared(Operation operation) {
        boolean inserted = jdbc.update("""
                INSERT INTO historical_order_book_source_retirement_operation
                    (operation_id, operation_fingerprint, provider, provider_symbol, market_id, depth,
                     approval_reference, actor, business_state, operational_state, preflight_fingerprint,
                     inventory_fingerprint, dependency_fingerprint, failure_reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (operation_fingerprint) DO NOTHING
                """, operation.operationId(), operation.operationFingerprint(), operation.scope().provider().name(),
                operation.scope().providerSymbol(), operation.scope().marketId(), operation.scope().depth(),
                operation.approvalReference(), operation.actor(), operation.businessState().name(),
                operation.operationalState().name(), operation.preflightFingerprint(), operation.inventoryFingerprint(),
                operation.dependencyFingerprint(), operation.failureReason(), Timestamp.from(operation.createdAt()),
                Timestamp.from(operation.updatedAt())) == 1;
        if (inserted) {
            jdbc.update("""
                    INSERT INTO historical_order_book_source_retirement_audit
                        (transition_id, operation_id, from_business_state, to_business_state,
                         from_operational_state, to_operational_state, actor, reason,
                         transition_fingerprint, external_reference, occurred_at)
                    VALUES (?, ?, NULL, 'PREPARED', 'NOT_STARTED', 'PREFLIGHT_PASSED', ?, ?, ?, NULL, ?)
                    """, UUID.randomUUID(), operation.operationId(), operation.actor(),
                    "exact-scope preflight passed", operation.operationFingerprint(),
                    Timestamp.from(operation.updatedAt()));
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public Preflight preflight(HistoricalEvidenceRetirementScope scope) {
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence
                """, Long.class);
        long outsideScope = jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_evidence
                 WHERE provider IS NULL OR provider <> ? OR provider_symbol IS NULL OR provider_symbol <> ?
                    OR market_id IS NULL OR market_id <> ? OR depth IS NULL OR depth <> ?
                """, Long.class, scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        long targetRows = total - outsideScope;
        long artifactCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_order_book_replay_artifact
                 WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?
                """, Long.class, scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        DependencyInventory dependencyInventory = jdbc.queryForObject("""
                WITH retirement_scope AS (
                    SELECT ?::varchar AS provider, ?::varchar AS provider_symbol,
                           ?::uuid AS market_id, ?::integer AS depth
                ), target_sequences AS (
                    SELECT DISTINCT evidence.sequence_id
                      FROM historical_order_book_evidence evidence
                      CROSS JOIN retirement_scope scope
                     WHERE evidence.provider = scope.provider
                       AND evidence.provider_symbol = scope.provider_symbol
                       AND evidence.market_id = scope.market_id
                       AND evidence.depth = scope.depth
                ), affected AS (
                    SELECT dependency.state, dependency.consumer_type, dependency.sequence_id,
                           artifact.provider AS artifact_provider,
                           artifact.provider_symbol AS artifact_provider_symbol,
                           artifact.market_id AS artifact_market_id,
                           artifact.depth AS artifact_depth,
                           CASE
                               WHEN dependency.sequence_id IN (SELECT sequence_id FROM target_sequences)
                                    AND (dependency.artifact_id IS NULL OR (
                                        artifact.provider = scope.provider
                                        AND artifact.provider_symbol = scope.provider_symbol
                                        AND artifact.market_id = scope.market_id
                                        AND artifact.depth = scope.depth))
                                    OR artifact.provider = scope.provider
                                    AND artifact.provider_symbol = scope.provider_symbol
                                    AND artifact.market_id = scope.market_id
                                    AND artifact.depth = scope.depth
                                    AND (dependency.sequence_id IS NULL
                                         OR dependency.sequence_id IN (SELECT sequence_id FROM target_sequences))
                               THEN TRUE ELSE FALSE
                           END AS exact_scope
                      FROM historical_order_book_evidence_dependency dependency
                      LEFT JOIN historical_order_book_replay_artifact artifact
                        ON artifact.artifact_id = dependency.artifact_id
                      CROSS JOIN retirement_scope scope
                     WHERE dependency.state NOT IN ('RELEASED', 'SOURCE_RETIRED')
                       AND (dependency.sequence_id IN (SELECT sequence_id FROM target_sequences)
                            OR (artifact.provider = scope.provider
                                AND artifact.provider_symbol = scope.provider_symbol
                                AND artifact.market_id = scope.market_id
                                AND artifact.depth = scope.depth))
                )
                SELECT COUNT(*) AS dependency_count,
                       COUNT(*) FILTER (WHERE state IN ('PROTECTED', 'PROMOTED')
                                             AND consumer_type = 'REPLAY_ARTIFACT'
                                             AND exact_scope) AS eligible_dependency_count,
                       COUNT(*) FILTER (WHERE consumer_type <> 'REPLAY_ARTIFACT') AS unknown_dependency_count,
                       COUNT(*) FILTER (WHERE NOT exact_scope
                                             OR state NOT IN ('PROTECTED', 'PROMOTED', 'HOLD'))
                           AS contradictory_dependency_count,
                       COUNT(*) FILTER (WHERE state = 'HOLD') AS hold_dependency_count
                  FROM affected
                """, (result, row) -> new DependencyInventory(
                result.getLong("dependency_count"), result.getLong("eligible_dependency_count"),
                result.getLong("unknown_dependency_count"), result.getLong("contradictory_dependency_count"),
                result.getLong("hold_dependency_count")),
                scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        long ambiguousSequenceCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM (
                      SELECT sequence_id
                        FROM historical_order_book_evidence
                       WHERE market_id = ? AND depth = ?
                       GROUP BY sequence_id
                      HAVING SUM(CASE WHEN provider = ? AND provider_symbol = ? THEN 0 ELSE 1 END) > 0
                         AND SUM(CASE WHEN provider = ? AND provider_symbol = ? THEN 1 ELSE 0 END) > 0
                  ) mixed_sequences
                """, Long.class, scope.marketId(), scope.depth(), scope.provider().name(), scope.providerSymbol(),
                scope.provider().name(), scope.providerSymbol());
        long retentionHoldCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM historical_order_book_retention_hold hold
                  LEFT JOIN historical_order_book_replay_artifact artifact ON artifact.artifact_id = hold.artifact_id
                  WHERE hold.released_at IS NULL
                    AND (artifact.provider = ? AND artifact.provider_symbol = ?
                         AND artifact.market_id = ? AND artifact.depth = ?)
                    OR hold.sequence_id IN (
                        SELECT sequence_id FROM historical_order_book_evidence
                         WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?
                    )
                """, Long.class, scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth(),
                scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        long holdCount = retentionHoldCount + dependencyInventory.holdDependencyCount();
        boolean catalogSafe = catalogSafe();
        String fingerprint = fingerprint(scope.provider().name() + "|" + scope.providerSymbol() + "|"
                + scope.marketId() + "|" + scope.depth() + "|" + total + "|" + targetRows + "|"
                + artifactCount + "|" + dependencyInventory.dependencyCount() + "|"
                + dependencyInventory.eligibleDependencyCount() + "|"
                + dependencyInventory.unknownDependencyCount() + "|"
                + dependencyInventory.contradictoryDependencyCount() + "|" + holdCount + "|"
                + ambiguousSequenceCount
                + "|" + catalogSafe);
        String inventoryFingerprint = inventoryFingerprint(scope);
        return new Preflight(scope, total, targetRows, outsideScope, artifactCount,
                dependencyInventory.dependencyCount(), dependencyInventory.eligibleDependencyCount(),
                dependencyInventory.unknownDependencyCount(), dependencyInventory.contradictoryDependencyCount(),
                holdCount, ambiguousSequenceCount, catalogSafe, fingerprint, inventoryFingerprint);
    }

    @Transactional(readOnly = true)
    public boolean approvalValid(HistoricalEvidenceRetirementScope scope, String approvalReference,
                                String actor, Instant now) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) > 0
                  FROM historical_order_book_source_retirement_approval
                 WHERE approval_reference = ? AND provider = ? AND provider_symbol = ?
                   AND market_id = ? AND depth = ? AND approved_actor = ?
                   AND revoked_at IS NULL AND valid_until > ?
                """, Boolean.class, approvalReference, scope.provider().name(), scope.providerSymbol(),
                scope.marketId(), scope.depth(), actor, Timestamp.from(now));
    }

    private String inventoryFingerprint(HistoricalEvidenceRetirementScope scope) {
        List<String> inventory = new ArrayList<>();
        jdbc.query("""
                SELECT sequence_id, status, invalidated_at, invalidation_reason, sealed_at, backfill_completed_at
                  FROM historical_order_book_sequence
                 WHERE sequence_id IN (
                     SELECT DISTINCT sequence_id FROM historical_order_book_evidence
                      WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?
                 )
                 ORDER BY sequence_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) result -> inventory.add("sequence|"
                        + result.getObject("sequence_id") + "|" + result.getString("status") + "|"
                        + result.getTimestamp("invalidated_at") + "|" + result.getString("invalidation_reason") + "|"
                        + result.getTimestamp("sealed_at") + "|" + result.getTimestamp("backfill_completed_at")),
                scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        jdbc.query("""
                SELECT artifact_id, semantic_identity, artifact_fingerprint, provider_symbol
                  FROM historical_order_book_replay_artifact
                 WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?
                 ORDER BY artifact_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) result -> inventory.add("artifact|" + result.getObject("artifact_id") + "|"
                        + result.getString("semantic_identity") + "|" + result.getString("artifact_fingerprint")
                        + "|" + result.getString("provider_symbol")), scope.provider().name(), scope.providerSymbol(),
                scope.marketId(), scope.depth());
        jdbc.query("""
                 SELECT dependency.dependency_id, dependency.dependency_key, dependency.state,
                        dependency.artifact_id, dependency.sequence_id, dependency.source_retirement_operation_id
                  FROM historical_order_book_evidence_dependency dependency
                  LEFT JOIN historical_order_book_replay_artifact artifact ON artifact.artifact_id = dependency.artifact_id
                 WHERE ((artifact.provider = ? AND artifact.provider_symbol = ? AND artifact.market_id = ? AND artifact.depth = ?)
                      OR dependency.sequence_id IN (SELECT sequence_id FROM historical_order_book_evidence
                           WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?))
                  ORDER BY dependency.dependency_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) result -> inventory.add("dependency|" + result.getObject("dependency_id") + "|"
                        + result.getString("dependency_key") + "|" + result.getString("state") + "|"
                        + result.getObject("artifact_id") + "|" + result.getObject("sequence_id") + "|"
                        + result.getObject("source_retirement_operation_id")), scope.provider().name(),
                scope.providerSymbol(), scope.marketId(), scope.depth(), scope.provider().name(),
                scope.providerSymbol(), scope.marketId(), scope.depth());
        jdbc.query("""
                 SELECT hold.hold_id, hold.hold_key, hold.artifact_id, hold.sequence_id, hold.released_at
                  FROM historical_order_book_retention_hold hold
                  LEFT JOIN historical_order_book_replay_artifact artifact ON artifact.artifact_id = hold.artifact_id
                 WHERE hold.released_at IS NULL
                   AND ((artifact.provider = ? AND artifact.provider_symbol = ? AND artifact.market_id = ? AND artifact.depth = ?)
                     OR hold.sequence_id IN (SELECT sequence_id FROM historical_order_book_evidence
                          WHERE provider = ? AND provider_symbol = ? AND market_id = ? AND depth = ?))
                  ORDER BY hold.hold_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) result -> inventory.add("hold|" + result.getObject("hold_id") + "|"
                        + result.getString("hold_key") + "|" + result.getObject("artifact_id") + "|"
                        + result.getObject("sequence_id") + "|" + result.getTimestamp("released_at")),
                scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth(),
                scope.provider().name(), scope.providerSymbol(), scope.marketId(), scope.depth());
        return fingerprint(String.join("\n", inventory));
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    private boolean catalogSafe() {
        try (var connection = dataSource.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().contains("H2")) {
                return false;
            }
        } catch (java.sql.SQLException exception) {
            return false;
        }
        Long inboundForeignKeys = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM pg_constraint constraint_row
                  JOIN pg_class relation ON relation.oid = constraint_row.confrelid
                  JOIN pg_namespace namespace_row ON namespace_row.oid = relation.relnamespace
                 WHERE constraint_row.contype = 'f'
                   AND namespace_row.nspname = 'public'
                   AND relation.relname = 'historical_order_book_evidence'
                """, Long.class);
        Long nonInternalTriggers = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM pg_trigger trigger_row
                  JOIN pg_class relation ON relation.oid = trigger_row.tgrelid
                  JOIN pg_namespace namespace_row ON namespace_row.oid = relation.relnamespace
                 WHERE NOT trigger_row.tgisinternal
                   AND namespace_row.nspname = 'public'
                   AND relation.relname = 'historical_order_book_evidence'
                """, Long.class);
        return inboundForeignKeys == 0 && nonInternalTriggers == 0;
    }

    private String fingerprint(String material) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    @Transactional
    public boolean transitionSourceRetired(UUID operationId, String dependencyFingerprint, String actor,
                                           String reason, Instant occurredAt, UUID transitionId,
                                           String transitionFingerprint) {
        int updated = jdbc.update("""
                UPDATE historical_order_book_source_retirement_operation
                   SET business_state = 'SOURCE_RETIRED', operational_state = 'PREFLIGHT_PASSED',
                       dependency_fingerprint = ?, actor = ?, updated_at = ?, failure_reason = NULL
                 WHERE operation_id = ? AND business_state = 'PREPARED'
                """, dependencyFingerprint, actor, Timestamp.from(occurredAt), operationId);
        if (updated == 0) {
            return find(operationId).map(value -> value.businessState()
                    == HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED).orElse(false);
        }
        jdbc.update("""
                INSERT INTO historical_order_book_source_retirement_audit
                    (transition_id, operation_id, from_business_state, to_business_state,
                     from_operational_state, to_operational_state, actor, reason,
                     transition_fingerprint, external_reference, occurred_at)
                VALUES (?, ?, 'PREPARED', 'SOURCE_RETIRED', 'PREFLIGHT_PASSED', 'PREFLIGHT_PASSED', ?, ?, ?, NULL, ?)
                ON CONFLICT (transition_fingerprint) DO NOTHING
                """, transitionId, operationId, actor, reason, transitionFingerprint, Timestamp.from(occurredAt));
        jdbc.update("""
                UPDATE historical_order_book_evidence_dependency dependency
                   SET state = 'SOURCE_RETIRED', source_retirement_operation_id = ?
                 WHERE dependency.state IN ('PROTECTED', 'PROMOTED', 'HOLD')
                   AND (
                       dependency.artifact_id IN (
                           SELECT artifact_id
                             FROM historical_order_book_replay_artifact
                            WHERE provider = (SELECT provider FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND provider_symbol = (SELECT provider_symbol FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND market_id = (SELECT market_id FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND depth = (SELECT depth FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                       )
                       OR dependency.sequence_id IN (
                           SELECT sequence_id FROM historical_order_book_evidence
                            WHERE provider = (SELECT provider FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND provider_symbol = (SELECT provider_symbol FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND market_id = (SELECT market_id FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                              AND depth = (SELECT depth FROM historical_order_book_source_retirement_operation WHERE operation_id = ?)
                       )
                   )
                """, operationId, operationId, operationId, operationId,
                operationId, operationId, operationId, operationId);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean hasAudit(UUID operationId, String toBusinessState, String toOperationalState,
                            String actor, String reason, String externalReference) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) > 0
                  FROM historical_order_book_source_retirement_audit
                 WHERE operation_id = ? AND to_business_state = ? AND to_operational_state = ?
                   AND actor = ? AND reason = ?
                   AND (external_reference = ? OR (external_reference IS NULL AND ? IS NULL))
                """, Boolean.class, operationId, toBusinessState, toOperationalState, actor, reason,
                externalReference, externalReference);
    }

    @Transactional
    public boolean transitionOperational(UUID operationId,
                                        HistoricalEvidenceRetirementOperationalState expected,
                                        HistoricalEvidenceRetirementOperationalState next,
                                        String actor, String reason, String externalReference,
                                        Instant occurredAt, UUID transitionId, String transitionFingerprint) {
        int updated = jdbc.update("""
                UPDATE historical_order_book_source_retirement_operation
                   SET operational_state = ?, actor = ?, updated_at = ?, failure_reason = ?
                 WHERE operation_id = ? AND operational_state = ?
                """, next.name(), actor, Timestamp.from(occurredAt),
                next == HistoricalEvidenceRetirementOperationalState.FAILED ? reason : null,
                operationId, expected.name());
        if (updated == 0) {
            return find(operationId).map(value -> value.operationalState() == next).orElse(false);
        }
        jdbc.update("""
                INSERT INTO historical_order_book_source_retirement_audit
                    (transition_id, operation_id, from_business_state, to_business_state,
                     from_operational_state, to_operational_state, actor, reason,
                     transition_fingerprint, external_reference, occurred_at)
                SELECT ?, operation_id, business_state, business_state, ?, ?, ?, ?, ?, ?, ?
                  FROM historical_order_book_source_retirement_operation
                 WHERE operation_id = ?
                ON CONFLICT (transition_fingerprint) DO NOTHING
                """, transitionId, expected.name(), next.name(), actor, reason, transitionFingerprint,
                externalReference, Timestamp.from(occurredAt), operationId);
        return true;
    }

    public record Operation(UUID operationId, String operationFingerprint, HistoricalEvidenceRetirementScope scope,
                            String approvalReference, String actor,
                            HistoricalEvidenceRetirementBusinessState businessState,
                            HistoricalEvidenceRetirementOperationalState operationalState,
                            String preflightFingerprint, String inventoryFingerprint, String dependencyFingerprint,
                            String failureReason, Instant createdAt, Instant updatedAt) {
    }

    public record Preflight(HistoricalEvidenceRetirementScope scope, long totalRows, long targetRows,
                             long outsideScopeRows, long artifactCount, long dependencyCount,
                             long eligibleDependencyCount, long unknownDependencyCount,
                             long contradictoryDependencyCount, long holdCount, long ambiguousSequenceCount,
                             boolean catalogSafe, String fingerprint,
                             String inventoryFingerprint) {
        public boolean passed() {
            return targetRows > 0 && dependencyCount == eligibleDependencyCount
                    && unknownDependencyCount == 0 && contradictoryDependencyCount == 0
                    && ambiguousSequenceCount == 0 && holdCount == 0 && catalogSafe;
        }
    }

    private record DependencyInventory(long dependencyCount, long eligibleDependencyCount,
                                       long unknownDependencyCount, long contradictoryDependencyCount,
                                       long holdDependencyCount) {
    }
}
