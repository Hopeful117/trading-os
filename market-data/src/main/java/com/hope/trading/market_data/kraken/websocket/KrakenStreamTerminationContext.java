package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.service.OrderBookEvidenceCaptureService;
import org.springframework.web.reactive.socket.CloseStatus;
import reactor.core.publisher.SignalType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KrakenStreamTerminationContext(
        UUID terminationId,
        UUID connectionId,
        String product,
        KrakenStreamTerminationClassification classification,
        SignalType signalType,
        Integer closeCode,
        String closeReason,
        String exceptionClass,
        String exceptionMessage,
        String rootCauseClass,
        String rootCauseMessage,
        Instant connectedAt,
        Instant terminatedAt,
        String intentionalReason,
        List<OrderBookEvidenceCaptureService.SequenceDiagnostics> sequences
) {
    public KrakenStreamTerminationContext {
        sequences = sequences == null ? List.of() : List.copyOf(sequences);
    }

    public static KrakenStreamTerminationContext create(
            UUID connectionId,
            String product,
            SignalType signalType,
            Throwable error,
            CloseStatus closeStatus,
            Instant connectedAt,
            Instant terminatedAt,
            KrakenStreamTerminationClassification classification,
            String intentionalReason,
            List<OrderBookEvidenceCaptureService.SequenceDiagnostics> sequences
    ) {
        Throwable rootCause = rootCause(error);
        return new KrakenStreamTerminationContext(
                UUID.randomUUID(), connectionId, product, classification, signalType,
                closeStatus == null ? null : closeStatus.getCode(),
                sanitize(closeStatus == null ? null : closeStatus.getReason()),
                error == null ? null : error.getClass().getName(),
                sanitize(error == null ? null : error.getMessage()),
                rootCause == null ? null : rootCause.getClass().getName(),
                sanitize(rootCause == null ? null : rootCause.getMessage()),
                connectedAt, terminatedAt, sanitize(intentionalReason, 64), sequences);
    }

    static String sanitize(String value) {
        return sanitize(value, 256);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current;
    }
}
