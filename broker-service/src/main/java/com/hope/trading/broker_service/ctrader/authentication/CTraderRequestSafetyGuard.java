package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public CTraderRequestSafetyGuard(JdbcTemplate jdbc, CTraderSafetyProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public SnapshotLease acquireSnapshot(String externalAccountId) {
        if (!properties.enabled()) return new SnapshotLease();

        String scope = "ACCOUNT:" + externalAccountId;
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
            throw new CTraderSafetyException("cTrader snapshot blocked by cooldown or concurrent synchronization");
        }
        return new SnapshotLease(scope, leaseToken, true);
    }

    @Transactional
    public void reserveRequest(String externalAccountId, int payloadType) {
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
            throw new CTraderSafetyException("cTrader daily request safety stop reached");
        }
        jdbc.update("""
                INSERT INTO ctrader_request_safety_event (occurred_at, request_day, external_account_id, payload_type)
                VALUES (?, ?, ?, ?)
                """, utc(now), day, externalAccountId, payloadType);

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
                        count, properties.alertThreshold(), payloadType);
            }
        }
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
}
