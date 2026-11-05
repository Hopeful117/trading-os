package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class CTraderRequestSafetyGuard {
    private static final Logger log = LoggerFactory.getLogger(CTraderRequestSafetyGuard.class);
    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final JdbcTemplate jdbc;
    private final CTraderSafetyProperties properties;
    private final Clock clock;
    private final MeterRegistry meters;

    public CTraderRequestSafetyGuard(JdbcTemplate jdbc, CTraderSafetyProperties properties, Clock clock) {
        this(jdbc, properties, clock, new SimpleMeterRegistry());
    }

    @Autowired
    public CTraderRequestSafetyGuard(JdbcTemplate jdbc, CTraderSafetyProperties properties, Clock clock,
                                     MeterRegistry meters) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.meters = meters;
    }

    public SnapshotLease acquireSnapshot(String externalAccountId) {
        return acquire(externalAccountId, "ACCOUNT:");
    }

    public SnapshotLease acquireExpectedMargin(String externalAccountId) {
        return acquire(externalAccountId, "MARGIN:");
    }

    private SnapshotLease acquire(String externalAccountId, String scopePrefix) {
        if (!properties.enabled()) return new SnapshotLease();

        String scope = scopePrefix + externalAccountId;
        Instant now = clock.instant();
        ensureScope(scope, now);
        String leaseToken = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE ctrader_request_safety
                   SET lease_token = ?, lease_until = ?, last_snapshot_at = ?
                 WHERE scope = ?
                   AND (lease_until IS NULL OR lease_until < ?)
                   AND (last_snapshot_at IS NULL OR last_snapshot_at <= ?)
                """, leaseToken, utc(now.plus(properties.snapshotLease())), utc(now), scope, utc(now),
                utc(now.minus(properties.snapshotCooldown())));
        if (claimed != 1) {
            meters.counter("ctrader.protocol.events", "event", "cooldown").increment();
            throw new CTraderSnapshotDeferredException(
                    "cTrader snapshot blocked by cooldown or concurrent synchronization");
        }
        return new SnapshotLease(scope, leaseToken, true);
    }

    @Transactional(noRollbackFor = CTraderSafetyException.class)
    public void reserveRequest(String externalAccountId, int payloadType) {
        reserveRequest(externalAccountId, RequestMetadata.unknown(payloadType));
    }

    @Transactional(noRollbackFor = CTraderSafetyException.class)
    public void reserveRequest(String externalAccountId, RequestMetadata metadata) {
        if (!properties.enabled()) return;

        Instant now = clock.instant();
        LocalDate day = LocalDate.now(clock);
        ensureScope(GLOBAL_SCOPE, now);
        jdbc.update("""
                UPDATE ctrader_request_safety
                   SET request_day = ?, request_count = 0, alert_logged = false
                 WHERE scope = ? AND request_day <> ?
                """, day, GLOBAL_SCOPE, day);
        int reserved = jdbc.update("""
                UPDATE ctrader_request_safety
                   SET request_count = request_count + 1
                 WHERE scope = ? AND request_count < ?
                """, GLOBAL_SCOPE, properties.effectiveRequestLimit());
        if (reserved != 1) {
            insertEvent(now, day, externalAccountId, metadata, "REJECTED");
            recordReservation(metadata, "rejected");
            log.warn("ctrader_request_budget_rejected operation={} trigger={} priority={} payloadType={} limit={}",
                    metadata.operation(), metadata.trigger(), metadata.priority(), metadata.payloadType(),
                    properties.effectiveRequestLimit());
            throw new CTraderSafetyException("cTrader daily request safety stop reached");
        }
        insertEvent(now, day, externalAccountId, metadata, "RESERVED");
        recordReservation(metadata, "reserved");

        Long count = jdbc.queryForObject(
                "SELECT request_count FROM ctrader_request_safety WHERE scope = ?",
                Long.class, GLOBAL_SCOPE);
        if (count != null && count >= properties.alertThreshold()) {
                int firstAlert = jdbc.update("""
                    UPDATE ctrader_request_safety
                       SET alert_logged = true
                     WHERE scope = ? AND alert_logged = false
                    """, GLOBAL_SCOPE);
            if (firstAlert == 1) {
                log.warn("ctrader_request_safety_alert count={} threshold={} payloadType={}",
                        count, properties.alertThreshold(), metadata.payloadType());
            }
        }
    }

    public void recordEmission(RequestMetadata metadata) {
        meters.counter("ctrader.protocol.emissions", "operation", metadata.operation(),
                "trigger", metadata.trigger(), "priority", metadata.priority()).increment();
    }

    public void recordSession(RequestMetadata metadata) {
        meters.counter("ctrader.protocol.events", "event", "session").increment();
    }

    public void recordRetry(RequestMetadata metadata) {
        meters.counter("ctrader.protocol.events", "event", "retry").increment();
    }

    private void recordReservation(RequestMetadata metadata, String outcome) {
        meters.counter("ctrader.protocol.reservations", "operation", metadata.operation(),
                "trigger", metadata.trigger(), "priority", metadata.priority(), "outcome", outcome).increment();
    }

    public BudgetStatus budgetStatus() {
        if (!properties.enabled()) {
            return new BudgetStatus(LocalDate.now(clock), 0, 0, properties.effectiveRequestLimit(),
                    properties.effectiveRequestLimit());
        }
        Instant now = clock.instant();
        LocalDate day = LocalDate.now(clock);
        ensureScope(GLOBAL_SCOPE, now);
        jdbc.update("""
                UPDATE ctrader_request_safety
                   SET request_day = ?, request_count = 0, alert_logged = false
                 WHERE scope = ? AND request_day <> ?
                """, day, GLOBAL_SCOPE, day);
        Long reserved = jdbc.queryForObject(
                "SELECT request_count FROM ctrader_request_safety WHERE scope = ?", Long.class, GLOBAL_SCOPE);
        Long rejected = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ctrader_request_safety_event
                 WHERE request_day = ? AND outcome = 'REJECTED'
                """, Long.class, day);
        long count = reserved == null ? 0 : reserved;
        long rejectedCount = rejected == null ? 0 : rejected;
        long limit = properties.effectiveRequestLimit();
        return new BudgetStatus(day, count, rejectedCount, limit, Math.max(0, limit - count));
    }

    private void insertEvent(Instant now, LocalDate day, String externalAccountId,
                             RequestMetadata metadata, String outcome) {
        jdbc.update("""
                INSERT INTO ctrader_request_safety_event
                    (occurred_at, request_day, external_account_id, payload_type,
                     operation, trigger, priority, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, utc(now), day, externalAccountId, metadata.payloadType(), metadata.operation(),
                metadata.trigger(), metadata.priority(), outcome);
    }

    private void ensureScope(String scope, Instant now) {
        jdbc.update("""
                INSERT INTO ctrader_request_safety
                    (scope, request_day, request_count, alert_logged, lease_until, lease_token, last_snapshot_at)
                VALUES (?, ?, 0, false, NULL, NULL, NULL)
                ON CONFLICT (scope) DO NOTHING
                """, scope, LocalDate.now(clock));
    }

    public void requireExecutionEnabled() {
        if (!properties.executionEnabled()) {
            throw new CTraderSafetyException("cTrader execution is disabled by safety policy");
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public final class SnapshotLease implements AutoCloseable {
        private final String scope;
        private final String leaseToken;
        private final boolean acquired;

        private SnapshotLease(String scope, String leaseToken, boolean acquired) {
            this.scope = scope;
            this.leaseToken = leaseToken;
            this.acquired = acquired;
        }

        private SnapshotLease() {
            this.scope = null;
            this.leaseToken = null;
            this.acquired = false;
        }

        @Override
        public void close() {
            if (!acquired) return;
            jdbc.update("""
                    UPDATE ctrader_request_safety
                       SET lease_token = NULL, lease_until = NULL
                     WHERE scope = ? AND lease_token = ?
                    """, scope, leaseToken);
        }
    }

    public record RequestMetadata(String operation, String trigger, String priority, int payloadType) {
        public RequestMetadata {
            operation = bounded(operation, "UNKNOWN_OPERATION");
            trigger = bounded(trigger, "UNKNOWN_TRIGGER");
            priority = bounded(priority, "NORMAL");
        }

        public static RequestMetadata unknown(int payloadType) {
            return new RequestMetadata("UNKNOWN_OPERATION", "UNKNOWN_TRIGGER", "NORMAL", payloadType);
        }

        public RequestMetadata withPayloadType(int value) {
            return new RequestMetadata(operation, trigger, priority, value);
        }

        private static String bounded(String value, String fallback) {
            String normalized = value == null || value.isBlank() ? fallback : value.trim();
            return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
        }
    }

    public record BudgetStatus(LocalDate requestDay, long reserved, long rejected,
                               long effectiveLimit, long remaining) { }
}
