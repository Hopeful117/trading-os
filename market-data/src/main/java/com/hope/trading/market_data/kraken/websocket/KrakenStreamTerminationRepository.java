package com.hope.trading.market_data.kraken.websocket;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;

@Repository
public class KrakenStreamTerminationRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public KrakenStreamTerminationRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Transactional
    public boolean append(KrakenStreamTerminationContext context) {
        if (exists(context)) return false;

        int inserted = jdbc.update(terminationInsertSql(),
                context.terminationId(), context.connectionId(), context.product(),
                context.classification().name(), context.signalType().name(), context.closeCode(),
                context.closeReason(), context.exceptionClass(), context.exceptionMessage(),
                context.rootCauseClass(), context.rootCauseMessage(), timestamp(context.connectedAt()),
                timestamp(context.terminatedAt()), context.intentionalReason(),
                context.transportEvidence().nettyChannelId(), context.transportEvidence().websocketCloseObserved(),
                context.transportEvidence().websocketCloseCode(), context.transportEvidence().websocketCloseReason(),
                context.transportEvidence().channelInactiveObserved(), timestamp(context.transportEvidence().channelInactiveAt()),
                context.transportEvidence().inputShutdownObserved(), context.transportEvidence().tlsCloseObserved(),
                context.transportEvidence().tlsCloseSucceeded(), context.transportEvidence().tlsExceptionClass(),
                context.transportEvidence().transportExceptionObserved(), context.transportEvidence().transportExceptionClass(),
                context.transportEvidence().transportRootCauseClass(), timestamp(context.terminatedAt()));
        if (inserted != 1) return false;

        context.sequences().forEach(sequence -> jdbc.update("""
                        INSERT INTO kraken_stream_termination_sequence (
                            termination_id, sequence_id, market_id, depth, provider_sequence,
                            local_ordinal, last_occurred_at, last_known_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                context.terminationId(), sequence.sequenceId(), sequence.marketId(), sequence.depth(),
                sequence.providerSequence(), sequence.localOrdinal(), timestamp(sequence.lastOccurredAt()),
                timestamp(sequence.lastKnownAt())));
        return true;
    }

    private boolean exists(KrakenStreamTerminationContext context) {
        return !jdbc.queryForList(
                "SELECT termination_id FROM kraken_stream_termination WHERE connection_id = ?",
                java.util.UUID.class, context.connectionId()).isEmpty();
    }

    private String terminationInsertSql() {
        String columns = """
                (termination_id, connection_id, product, classification, signal_type,
                 close_code, close_reason, exception_class, exception_message,
                 root_cause_class, root_cause_message, connected_at, terminated_at,
                 intentional_reason, netty_channel_id, websocket_close_observed,
                 websocket_close_code, websocket_close_reason, channel_inactive_observed,
                 channel_inactive_at, input_shutdown_observed, tls_close_observed,
                 tls_close_succeeded, tls_exception_class, transport_exception_observed,
                 transport_exception_class, transport_root_cause_class, created_at)
                """;
        String values = "VALUES (" + "?, ".repeat(27) + "?)";
        if (isH2()) {
            return "MERGE INTO kraken_stream_termination " + columns + " KEY(connection_id) " + values;
        }
        return "INSERT INTO kraken_stream_termination " + columns + " " + values
                + " ON CONFLICT (connection_id) DO NOTHING";
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("H2");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to determine database product", exception);
        }
    }
}
