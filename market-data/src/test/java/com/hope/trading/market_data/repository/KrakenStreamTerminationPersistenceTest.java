package com.hope.trading.market_data.repository;

import com.hope.trading.market_data.kraken.websocket.KrakenStreamTerminationClassification;
import com.hope.trading.market_data.kraken.websocket.KrakenStreamTerminationContext;
import com.hope.trading.market_data.kraken.websocket.KrakenStreamTerminationRepository;
import com.hope.trading.market_data.kraken.websocket.KrakenTransportTerminationEvidence;
import com.hope.trading.market_data.helper.InstrumentProductType;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.helper.TradingStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketConstraints;
import com.hope.trading.market_data.model.MarketState;
import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.service.OrderBookEvidenceFingerprint;
import com.hope.trading.market_data.service.OrderBookEvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.SignalType;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KrakenStreamTerminationPersistenceTest {
    @Autowired
    private KrakenStreamTerminationRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MarketRepository markets;
    @Autowired
    private OrderBookEvidenceRepository evidenceRepository;

    @Test
    void persistsTerminationContextAndIsIdempotentByConnection() {
        UUID connectionId = UUID.randomUUID();
        KrakenStreamTerminationContext context = KrakenStreamTerminationContext.create(
                connectionId, "FUTURES", SignalType.ON_COMPLETE,
                null, null, Instant.parse("2026-10-19T01:00:00Z"),
                Instant.parse("2026-10-19T01:21:00Z"),
                KrakenStreamTerminationClassification.UNKNOWN_TRANSPORT_COMPLETION,
                null, new KrakenTransportTerminationEvidence(
                        "channel-1", true, 1001, "going away", true,
                        Instant.parse("2026-10-19T01:21:00Z"), false, true, true,
                        null, false, null, null), null);

        assertThat(repository.append(context)).isTrue();
        assertThat(repository.append(context)).isFalse();

        var row = jdbc.queryForMap("""
                 SELECT product, classification, signal_type, close_code, close_reason,
                        exception_class, connected_at, terminated_at, netty_channel_id,
                        websocket_close_observed, websocket_close_code, websocket_close_reason,
                        channel_inactive_observed
                FROM kraken_stream_termination
                WHERE connection_id = ?
                """, connectionId);

        assertThat(row.get("PRODUCT")).isEqualTo("FUTURES");
        assertThat(row.get("CLASSIFICATION")).isEqualTo("UNKNOWN_TRANSPORT_COMPLETION");
        assertThat(row.get("SIGNAL_TYPE")).isEqualTo("ON_COMPLETE");
        assertThat(row.get("CLOSE_CODE")).isNull();
        assertThat(row.get("CLOSE_REASON")).isNull();
        assertThat(row.get("EXCEPTION_CLASS")).isNull();
        assertThat(row.get("CONNECTED_AT")).isNotNull();
        assertThat(row.get("TERMINATED_AT")).isNotNull();
        assertThat(row.get("NETTY_CHANNEL_ID")).isEqualTo("channel-1");
        assertThat(row.get("WEBSOCKET_CLOSE_OBSERVED")).isEqualTo(true);
        assertThat(row.get("WEBSOCKET_CLOSE_CODE")).isEqualTo(1001);
        assertThat(row.get("WEBSOCKET_CLOSE_REASON")).isEqualTo("going away");
        assertThat(row.get("CHANNEL_INACTIVE_OBSERVED")).isEqualTo(true);
    }

    @Test
    void persistsCorrelationWithTheActiveOrderBookSequence() {
        UUID marketId = markets.saveAndFlush(market()).getMarketId();
        UUID sequenceId = UUID.randomUUID();
        OrderBookEvidence evidence = evidence(marketId, sequenceId);
        assertThat(evidenceRepository.append(evidence)).isTrue();

        var diagnostics = new com.hope.trading.market_data.service.OrderBookEvidenceCaptureService.SequenceDiagnostics(
                sequenceId, marketId, 25, 100L, 3L,
                evidence.occurredAt(), evidence.knownAt());
        KrakenStreamTerminationContext context = KrakenStreamTerminationContext.create(
                UUID.randomUUID(), "FUTURES", SignalType.ON_COMPLETE, null, null,
                evidence.knownAt(), evidence.knownAt().plusSeconds(1),
                KrakenStreamTerminationClassification.UNKNOWN_TRANSPORT_COMPLETION,
                null, List.of(diagnostics));

        assertThat(repository.append(context)).isTrue();
        var row = jdbc.queryForMap("""
                SELECT sequence_id, market_id, depth, provider_sequence, local_ordinal
                FROM kraken_stream_termination_sequence
                WHERE termination_id = ? AND sequence_id = ?
                """, context.terminationId(), sequenceId);

        assertThat(row.get("SEQUENCE_ID")).isEqualTo(sequenceId);
        assertThat(row.get("MARKET_ID")).isEqualTo(marketId);
        assertThat(row.get("DEPTH")).isEqualTo(25);
        assertThat(row.get("PROVIDER_SEQUENCE")).isEqualTo(100L);
        assertThat(row.get("LOCAL_ORDINAL")).isEqualTo(3L);
    }

    private OrderBookEvidence evidence(UUID marketId, UUID sequenceId) {
        OrderBookEvidence value = new OrderBookEvidence(
                UUID.randomUUID(), marketId, MarketProvider.KRAKEN, "BTC/USD", "PF_XBTUSD", 25,
                OrderBookEvidenceType.SNAPSHOT, OrderBookEvidenceOrigin.PROVIDER, null, null,
                sequenceId, 0L, 100L, Instant.parse("2026-10-19T01:00:00Z"),
                Instant.parse("2026-10-19T01:00:01Z"),
                List.of(level("100", "1")), List.of(level("101", "1")), null,
                OrderBookEvidenceFingerprint.NORMALIZATION_VERSION, "pending");
        return new OrderBookEvidence(value.evidenceId(), value.marketId(), value.provider(), value.symbol(),
                value.providerSymbol(), value.depth(), value.evidenceType(), value.origin(),
                value.sourceStartOrdinal(), value.sourceEndOrdinal(), value.sequenceId(), value.sequenceOrdinal(),
                value.providerSequence(), value.occurredAt(), value.knownAt(), value.bids(), value.asks(),
                value.checksum(), value.normalizationVersion(), OrderBookEvidenceFingerprint.fingerprint(value));
    }

    private OrderBookLevel level(String price, String quantity) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(quantity));
    }

    private Market market() {
        return Market.builder()
                .provider(MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .providerSymbol("PF_XBTUSD")
                .baseAsset("BTC")
                .quoteAsset("USD")
                .productType(InstrumentProductType.LINEAR_PERPETUAL)
                .marketConstraints(MarketConstraints.builder().build())
                .marketState(MarketState.builder().tradingStatus(TradingStatus.OPEN)
                        .tradable(true).lastUpdated(Instant.parse("2026-10-19T00:59:00Z")).build())
                .build();
    }
}
