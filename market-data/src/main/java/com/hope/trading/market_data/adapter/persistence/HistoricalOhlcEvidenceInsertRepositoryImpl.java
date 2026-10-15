package com.hope.trading.market_data.adapter.persistence;

import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

@Repository
class HistoricalOhlcEvidenceInsertRepositoryImpl implements HistoricalOhlcEvidenceInsertRepository {
    private static final Object H2_INSERT_LOCK = new Object();
    private static final String COLUMNS = """
            (observation_id, market_id, interval_name, open_time, close_time,
             open, high, low, close, volume, vwap, trade_count, closed, synthetic,
             provider, provider_symbol, source_id, source_updated_at, fetched_at,
             captured_at, known_at, origin, normalization_version, observation_fingerprint)
            """;
    private static final String VALUES = """
            (:observationId, :marketId, :intervalName, :openTime, :closeTime,
             :open, :high, :low, :close, :volume, :vwap, :tradeCount, :closed, :synthetic,
             :provider, :providerSymbol, :sourceId, :sourceUpdatedAt, :fetchedAt,
             :capturedAt, :knownAt, :origin, :normalizationVersion, :observationFingerprint)
    """;

    private final EntityManager entityManager;
    private final DataSource dataSource;

    HistoricalOhlcEvidenceInsertRepositoryImpl(EntityManager entityManager, DataSource dataSource) {
        this.entityManager = entityManager;
        this.dataSource = dataSource;
    }

    @Override
    public int insertIfAbsent(HistoricalOhlcEvidence evidence) {
        if (isH2()) {
            synchronized (H2_INSERT_LOCK) {
                if (alreadyExists(evidence)) {
                    return 0;
                }
                return executeInsert(h2Sql(), evidence);
            }
        }
        return executeInsert(postgresSql(), evidence);
    }

    private int executeInsert(String sql, HistoricalOhlcEvidence evidence) {
        var query = entityManager.createNativeQuery(sql);
        query.setParameter("observationId", evidence.observationId());
        query.setParameter("marketId", evidence.marketId());
        query.setParameter("intervalName", evidence.interval().name());
        query.setParameter("openTime", evidence.openTime());
        query.setParameter("closeTime", evidence.closeTime());
        query.setParameter("open", evidence.open());
        query.setParameter("high", evidence.high());
        query.setParameter("low", evidence.low());
        query.setParameter("close", evidence.close());
        query.setParameter("volume", evidence.volume());
        query.setParameter("vwap", evidence.vwap());
        query.setParameter("tradeCount", evidence.tradeCount());
        query.setParameter("closed", evidence.closed());
        query.setParameter("synthetic", evidence.synthetic());
        query.setParameter("provider", evidence.provider().name());
        query.setParameter("providerSymbol", evidence.providerSymbol());
        query.setParameter("sourceId", evidence.sourceId());
        query.setParameter("sourceUpdatedAt", evidence.sourceUpdatedAt());
        query.setParameter("fetchedAt", evidence.fetchedAt());
        query.setParameter("capturedAt", evidence.capturedAt());
        query.setParameter("knownAt", evidence.knownAt());
        query.setParameter("origin", evidence.origin().name());
        query.setParameter("normalizationVersion", evidence.normalizationVersion());
        query.setParameter("observationFingerprint", evidence.observationFingerprint());
        return query.executeUpdate();
    }

    private boolean alreadyExists(HistoricalOhlcEvidence evidence) {
        return !entityManager.createNativeQuery(
                        "SELECT observation_id FROM historical_ohlc_evidence "
                                + "WHERE observation_fingerprint = :observationFingerprint")
                .setParameter("observationFingerprint", evidence.observationFingerprint())
                .getResultList()
                .isEmpty();
    }

    private String postgresSql() {
        return "INSERT INTO historical_ohlc_evidence " + COLUMNS + " VALUES " + VALUES
                + " ON CONFLICT (observation_fingerprint) DO NOTHING";
    }

    private String h2Sql() {
        return "MERGE INTO historical_ohlc_evidence " + COLUMNS
                + " KEY(observation_fingerprint) VALUES " + VALUES;
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }
}
