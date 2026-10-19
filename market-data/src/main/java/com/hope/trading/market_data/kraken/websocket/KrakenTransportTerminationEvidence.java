package com.hope.trading.market_data.kraken.websocket;

import java.time.Instant;

/** Immutable, bounded low-level evidence captured for one physical connection. */
public record KrakenTransportTerminationEvidence(
        String nettyChannelId,
        Boolean websocketCloseObserved,
        Integer websocketCloseCode,
        String websocketCloseReason,
        Boolean channelInactiveObserved,
        Instant channelInactiveAt,
        Boolean inputShutdownObserved,
        Boolean tlsCloseObserved,
        Boolean tlsCloseSucceeded,
        String tlsExceptionClass,
        Boolean transportExceptionObserved,
        String transportExceptionClass,
        String transportRootCauseClass
) {
    public KrakenTransportTerminationEvidence {
        nettyChannelId = sanitize(nettyChannelId, 128);
        websocketCloseReason = sanitize(websocketCloseReason, 256);
        tlsExceptionClass = sanitize(tlsExceptionClass, 255);
        transportExceptionClass = sanitize(transportExceptionClass, 255);
        transportRootCauseClass = sanitize(transportRootCauseClass, 255);
    }

    public static KrakenTransportTerminationEvidence empty() {
        return new KrakenTransportTerminationEvidence(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
