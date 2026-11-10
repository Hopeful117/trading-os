package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookRetentionClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SourceRetirementAcknowledgementService {
    private final JdbcTemplate jdbc;
    private final OrderBookRetentionClient retentionClient;
    private final Clock clock;

    public SourceRetirementAcknowledgementService(JdbcTemplate jdbc,
                                                   OrderBookRetentionClient retentionClient,
                                                   Clock clock) {
        this.jdbc = jdbc;
        this.retentionClient = retentionClient;
        this.clock = clock;
    }

    @Transactional
    public Acknowledgement acknowledge(Request request) {
        validate(request);
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO historical_source_retirement_acknowledgement
                    (operation_id, provider, provider_symbol, market_id, depth,
                     dependency_fingerprint, state, actor, reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACKNOWLEDGED', ?, ?, ?, ?)
                ON CONFLICT (operation_id) DO NOTHING
                """, request.operationId(), request.provider(), request.providerSymbol(), request.marketId(),
                request.depth(), request.dependencyFingerprint(), request.actor(), request.reason(),
                Timestamp.from(now), Timestamp.from(now));
        if (inserted == 0 && !matchesExisting(request)) {
            throw new IllegalStateException("source-retirement acknowledgement conflicts with existing operation");
        }
        try {
            OrderBookRetentionClient.SourceRetirementAcknowledgement response = retentionClient
                    .acknowledgeSourceRetirement(request.operationId(),
                            new OrderBookRetentionClient.SourceRetirementAcknowledgementRequest(
                                    request.dependencyFingerprint(), "market-intelligence", request.reason()));
            jdbc.update("""
                    UPDATE historical_source_retirement_acknowledgement
                       SET state = 'CONFIRMED', updated_at = ?
                     WHERE operation_id = ? AND dependency_fingerprint = ?
                    """, Timestamp.from(clock.instant()), request.operationId(), request.dependencyFingerprint());
            return new Acknowledgement(request.operationId(), response.dependencyFingerprint(), "CONFIRMED",
                    response.reason());
        } catch (RuntimeException exception) {
            jdbc.update("""
                    UPDATE historical_source_retirement_acknowledgement
                       SET state = 'RECONCILIATION_REQUIRED', updated_at = ?
                    WHERE operation_id = ?
                    """, Timestamp.from(clock.instant()), request.operationId());
            return new Acknowledgement(request.operationId(), request.dependencyFingerprint(),
                    "RECONCILIATION_REQUIRED", "remote acknowledgement requires reconciliation");
        }
    }

    private boolean matchesExisting(Request request) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM historical_source_retirement_acknowledgement
                 WHERE operation_id = ? AND provider = ? AND provider_symbol = ?
                   AND market_id = ? AND depth = ? AND dependency_fingerprint = ?
                """, Long.class, request.operationId(), request.provider(), request.providerSymbol(),
                request.marketId(), request.depth(), request.dependencyFingerprint()) > 0;
    }

    private void validate(Request request) {
        if (request == null || request.operationId() == null || request.marketId() == null
                || request.depth() < 1 || blank(request.provider()) || blank(request.providerSymbol())
                || blank(request.dependencyFingerprint()) || blank(request.actor()) || blank(request.reason())) {
            throw new IllegalArgumentException("complete source-retirement acknowledgement is required");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Request(UUID operationId, String provider, String providerSymbol, UUID marketId, int depth,
                          String dependencyFingerprint, String actor, String reason) {
    }

    public record Acknowledgement(UUID operationId, String dependencyFingerprint, String state, String reason) {
    }
}
