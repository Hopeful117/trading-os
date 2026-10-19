package com.hope.trading.market_data.kraken.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenTransportTerminationEvidenceTest {
    @Test
    void genericEvidenceCannotOverwriteSpecificEvidence() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();

        evidence.websocketClose(1001, "remote close");
        evidence.websocketClose(1000, "generic close");
        evidence.channelInactive(java.time.Instant.parse("2026-10-19T12:00:00Z"));

        var snapshot = evidence.snapshot();
        assertThat(snapshot.websocketCloseCode()).isEqualTo(1001);
        assertThat(snapshot.websocketCloseReason()).isEqualTo("remote close");
        assertThat(snapshot.channelInactiveObserved()).isTrue();
    }

    @Test
    void emptyAccumulatorDoesNotInventHistoricalEvidence() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();

        var snapshot = evidence.snapshot();

        assertThat(snapshot.nettyChannelId()).isNull();
        assertThat(snapshot.websocketCloseObserved()).isNull();
        assertThat(snapshot.channelInactiveObserved()).isNull();
    }

    @Test
    void finalizationClosesTheAccumulatorAgainstLateCallbacks() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();
        evidence.websocketClose(1001, "remote close");

        var finalized = evidence.snapshotAndFinalize();
        evidence.transportException(new IllegalStateException("late failure"));

        assertThat(finalized.websocketCloseObserved()).isTrue();
        assertThat(finalized.transportExceptionObserved()).isFalse();
        assertThat(evidence.snapshot().transportExceptionObserved()).isFalse();
    }
}
