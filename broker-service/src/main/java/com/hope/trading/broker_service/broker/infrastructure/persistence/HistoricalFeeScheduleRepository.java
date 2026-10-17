package com.hope.trading.broker_service.broker.infrastructure.persistence;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleFingerprint;
import com.hope.trading.broker_service.broker.domain.model.FeeRateUnit;
import com.hope.trading.broker_service.broker.application.port.HistoricalFeeScheduleReader;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HistoricalFeeScheduleRepository implements HistoricalFeeScheduleReader {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public HistoricalFeeScheduleRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public synchronized HistoricalFeeSchedule append(HistoricalFeeSchedule schedule) {
        String expectedFingerprint = HistoricalFeeScheduleFingerprint.fingerprint(schedule);
        if (!expectedFingerprint.equals(schedule.scheduleFingerprint())) {
            throw new IllegalArgumentException("schedule fingerprint does not match canonical evidence");
        }
        HistoricalFeeSchedule existing = findByFingerprint(schedule.scheduleFingerprint());
        if (existing != null) return existing;
        if (schedule.origin() == HistoricalFeeScheduleOrigin.CAPTURED && !findOverlapping(schedule).isEmpty()) {
            throw new IllegalArgumentException("overlapping captured fee schedule exists");
        }

        jdbc.update(insertSql(), schedule.scheduleId(), schedule.brokerAccountId(), schedule.provider().name(),
                schedule.instrument(), schedule.productType(), Timestamp.from(schedule.effectiveFrom()),
                timestamp(schedule.effectiveTo()), schedule.makerRate(), schedule.takerRate(), schedule.rateUnit().name(),
                schedule.settlementAsset(),
                schedule.sourceId(), Timestamp.from(schedule.observedAt()), Timestamp.from(schedule.knownAt()),
                schedule.origin().name(), schedule.normalizationVersion(), schedule.scheduleFingerprint());
        return findByFingerprint(schedule.scheduleFingerprint());
    }

    @Override
    public Optional<HistoricalFeeSchedule> findEligible(UUID brokerAccountId, BrokerProviderId provider,
                                                         String productType, String instrument,
                                                         Instant executionTime, Instant researchCutoff) {
        return jdbc.query("""
                        SELECT schedule_id, broker_account_id, provider, instrument, product_type,
                               effective_from, effective_to, maker_rate, taker_rate, rate_unit, settlement_asset,
                               source_id, observed_at, known_at, origin, normalization_version,
                               schedule_fingerprint
                        FROM historical_fee_schedule
                        WHERE broker_account_id = ? AND provider = ? AND product_type = ? AND instrument = ?
                          AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                          AND known_at <= ? AND origin = 'CAPTURED'
                        ORDER BY effective_from DESC, known_at DESC, schedule_fingerprint ASC
                        """, this::map, brokerAccountId, provider.name(), productType, instrument,
                Timestamp.from(executionTime), Timestamp.from(executionTime),
                Timestamp.from(researchCutoff)).stream().findFirst();
    }

    private HistoricalFeeSchedule findByFingerprint(String fingerprint) {
        List<HistoricalFeeSchedule> result = jdbc.query("""
                        SELECT schedule_id, broker_account_id, provider, instrument, product_type,
                               effective_from, effective_to, maker_rate, taker_rate, rate_unit, settlement_asset,
                               source_id, observed_at, known_at, origin, normalization_version,
                               schedule_fingerprint
                        FROM historical_fee_schedule WHERE schedule_fingerprint = ?
                        """, this::map, fingerprint);
        return result.isEmpty() ? null : result.getFirst();
    }

    private List<HistoricalFeeSchedule> findOverlapping(HistoricalFeeSchedule schedule) {
        return jdbc.query("""
                        SELECT schedule_id, broker_account_id, provider, instrument, product_type,
                               effective_from, effective_to, maker_rate, taker_rate, rate_unit, settlement_asset,
                               source_id, observed_at, known_at, origin, normalization_version,
                               schedule_fingerprint
                        FROM historical_fee_schedule
                        WHERE broker_account_id = ? AND provider = ? AND instrument = ? AND product_type = ?
                          AND origin = 'CAPTURED'
                          AND effective_from < COALESCE(?, TIMESTAMP '9999-12-31 23:59:59')
                          AND (effective_to IS NULL OR effective_to > ?)
                        """, this::map, schedule.brokerAccountId(), schedule.provider().name(),
                schedule.instrument(), schedule.productType(), timestamp(schedule.effectiveTo()),
                Timestamp.from(schedule.effectiveFrom()));
    }

    private HistoricalFeeSchedule map(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        return new HistoricalFeeSchedule(result.getObject("schedule_id", UUID.class),
                result.getObject("broker_account_id", UUID.class),
                BrokerProviderId.valueOf(result.getString("provider")), result.getString("instrument"),
                result.getString("product_type"), result.getTimestamp("effective_from").toInstant(),
                timestamp(result, "effective_to"), result.getBigDecimal("maker_rate"),
                result.getBigDecimal("taker_rate"), FeeRateUnit.valueOf(result.getString("rate_unit")),
                result.getString("settlement_asset"),
                result.getString("source_id"), result.getTimestamp("observed_at").toInstant(),
                result.getTimestamp("known_at").toInstant(),
                HistoricalFeeScheduleOrigin.valueOf(result.getString("origin")),
                result.getString("normalization_version"), result.getString("schedule_fingerprint"));
    }

    private String insertSql() {
        String columns = "(schedule_id, broker_account_id, provider, instrument, product_type, effective_from, "
                + "effective_to, maker_rate, taker_rate, rate_unit, settlement_asset, source_id, observed_at, known_at, "
                + "origin, normalization_version, schedule_fingerprint)";
        String values = "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (isH2()) return "MERGE INTO historical_fee_schedule " + columns + " KEY(schedule_fingerprint) " + values;
        return "INSERT INTO historical_fee_schedule " + columns + " " + values
                + " ON CONFLICT (schedule_fingerprint) DO NOTHING";
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant timestamp(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
