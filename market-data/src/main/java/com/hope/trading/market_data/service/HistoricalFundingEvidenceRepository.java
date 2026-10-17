package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalFundingEvidence;
import com.hope.trading.market_data.model.HistoricalFundingEvidenceOrigin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class HistoricalFundingEvidenceRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public HistoricalFundingEvidenceRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Transactional
    public boolean append(HistoricalFundingEvidence evidence) {
        if (exists(evidence.evidenceFingerprint())) {
            return false;
        }
        int rows = jdbc.update(insertSql(),
                evidence.evidenceId(), evidence.marketId(), evidence.provider().name(), evidence.symbol(),
                evidence.providerSymbol(), evidence.productType().name(), Timestamp.from(evidence.fundingTime()),
                Timestamp.from(evidence.effectiveFrom()), evidence.effectiveTo() == null ? null : Timestamp.from(evidence.effectiveTo()),
                evidence.fundingRate(), evidence.settlementAsset(),
                evidence.sourceId(), evidence.sourceUpdatedAt() == null ? null : Timestamp.from(evidence.sourceUpdatedAt()),
                Timestamp.from(evidence.fetchedAt()), Timestamp.from(evidence.capturedAt()),
                Timestamp.from(evidence.knownAt()), evidence.origin().name(), evidence.normalizationVersion(),
                evidence.evidenceFingerprint());
        return rows == 1;
    }

    private boolean exists(String fingerprint) {
        return !jdbc.queryForList(
                "SELECT evidence_id FROM historical_funding_evidence WHERE evidence_fingerprint = ?",
                UUID.class, fingerprint).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<HistoricalFundingEvidence> findStrictObserved(
            UUID marketId,
            Instant asOf,
            int limit
    ) {
        if (marketId == null || asOf == null) {
            throw new IllegalArgumentException("marketId and asOf are required");
        }
        if (limit < 1 || limit > 720) {
            throw new IllegalArgumentException("Funding evidence limit must be between 1 and 720");
        }
        return jdbc.query("""
                        SELECT evidence_id, market_id, provider, symbol, provider_symbol,
                               product_type, funding_time, effective_from, effective_to, funding_rate, settlement_asset,
                               source_id, source_updated_at,
                               fetched_at, captured_at, known_at, origin,
                               normalization_version, evidence_fingerprint
                        FROM historical_funding_evidence
                        WHERE market_id = ?
                          AND funding_time <= ?
                          AND known_at <= ?
                          AND origin = 'CAPTURED'
                        ORDER BY funding_time DESC, known_at DESC, evidence_id DESC
                        LIMIT ?
                        """,
                (result, row) -> new HistoricalFundingEvidence(
                        result.getObject("evidence_id", UUID.class),
                        result.getObject("market_id", UUID.class),
                        MarketProvider.valueOf(result.getString("provider")),
                         result.getString("symbol"), result.getString("provider_symbol"),
                         com.hope.trading.market_data.helper.InstrumentProductType.valueOf(result.getString("product_type")),
                         result.getTimestamp("funding_time").toInstant(), timestamp(result, "effective_from"),
                         timestamp(result, "effective_to"), result.getBigDecimal("funding_rate"),
                         result.getString("settlement_asset"), result.getString("source_id"),
                        timestamp(result, "source_updated_at"), timestamp(result, "fetched_at"),
                        timestamp(result, "captured_at"), timestamp(result, "known_at"),
                        HistoricalFundingEvidenceOrigin.valueOf(result.getString("origin")),
                        result.getString("normalization_version"), result.getString("evidence_fingerprint")),
                marketId, Timestamp.from(asOf), Timestamp.from(asOf), limit);
    }

    private String insertSql() {
        String columns = "(evidence_id, market_id, provider, symbol, provider_symbol, product_type, funding_time, "
                + "effective_from, effective_to, funding_rate, settlement_asset, source_id, source_updated_at, "
                + "fetched_at, captured_at, known_at, origin, normalization_version, evidence_fingerprint)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) {
            return "MERGE INTO historical_funding_evidence " + columns + " KEY(evidence_fingerprint) " + values;
        }
        return "INSERT INTO historical_funding_evidence " + columns + " " + values
                + " ON CONFLICT (evidence_fingerprint) DO NOTHING";
    }

    private static Instant timestamp(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }
}
