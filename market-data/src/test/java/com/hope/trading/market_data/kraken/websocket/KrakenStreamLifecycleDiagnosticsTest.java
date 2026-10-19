package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.service.OrderBookEvidenceCaptureService;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.CloseStatus;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenStreamLifecycleDiagnosticsTest {
    @Test
    void preservesErrorAndRootCauseContext() {
        Throwable root = new IllegalStateException("transport closed");
        Throwable error = new RuntimeException("stream failed", root);
        var sequence = new OrderBookEvidenceCaptureService.SequenceDiagnostics(
                UUID.randomUUID(), UUID.randomUUID(), 25, 11L, 1L,
                Instant.parse("2026-10-16T11:59:59Z"), Instant.parse("2026-10-16T12:00:00Z"));

        var context = KrakenStreamLifecycleDiagnostics.terminationContext(
                SignalType.ON_ERROR, error, sequence);

        assertThat(context.signalType()).isEqualTo(SignalType.ON_ERROR);
        assertThat(context.exceptionClass()).isEqualTo(RuntimeException.class.getName());
        assertThat(context.exceptionMessage()).isEqualTo("stream failed");
        assertThat(context.rootCauseClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(context.rootCauseMessage()).isEqualTo("transport closed");
        assertThat(context.occurredToKnownLag()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void preservesNormalCompletionAndCancellationWithoutAnException() {
        var complete = KrakenStreamLifecycleDiagnostics.terminationContext(
                SignalType.ON_COMPLETE, null, null);
        var cancel = KrakenStreamLifecycleDiagnostics.terminationContext(
                SignalType.CANCEL, null, null);

        assertThat(complete.signalType()).isEqualTo(SignalType.ON_COMPLETE);
        assertThat(complete.exceptionClass()).isNull();
        assertThat(cancel.signalType()).isEqualTo(SignalType.CANCEL);
        assertThat(cancel.exceptionMessage()).isNull();
    }

    @Test
    void boundsAndSanitizesExceptionMessages() {
        String message = "line-one\nline-two\t" + "x".repeat(400);
        var context = KrakenStreamLifecycleDiagnostics.terminationContext(
                SignalType.ON_ERROR, new IllegalStateException(message), null);

        assertThat(context.exceptionMessage()).hasSize(256);
        assertThat(context.exceptionMessage()).doesNotContain("\n", "\r", "\t");
    }

    @Test
    void classifiesExceptionFreeCompletionAsUnknownAndPreservesCloseMetadata() {
        var context = KrakenStreamTerminationContext.create(
                UUID.randomUUID(), "FUTURES", SignalType.ON_COMPLETE, null,
                new CloseStatus(1000, "remote\nclose"), Instant.parse("2026-10-16T11:59:00Z"),
                Instant.parse("2026-10-16T12:00:00Z"),
                KrakenStreamTerminationClassification.UNKNOWN_TRANSPORT_COMPLETION, null, null);

        assertThat(context.classification())
                .isEqualTo(KrakenStreamTerminationClassification.UNKNOWN_TRANSPORT_COMPLETION);
        assertThat(context.closeCode()).isEqualTo(1000);
        assertThat(context.closeReason()).isEqualTo("remote close");
    }

    @Test
    void boundsIntentAndExceptionMetadata() {
        String value = "x".repeat(400);
        var context = KrakenStreamTerminationContext.create(
                UUID.randomUUID(), "FUTURES", SignalType.ON_ERROR,
                new IllegalStateException(value), null, null, Instant.now(),
                KrakenStreamTerminationClassification.NETWORK_FAILURE, value, null);

        assertThat(context.exceptionMessage()).hasSize(256);
        assertThat(context.intentionalReason()).hasSize(64);
    }
}
