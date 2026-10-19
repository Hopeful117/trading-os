package com.hope.trading.market_data.kraken.websocket;

import java.time.Instant;

/** Thread-safe monotonic accumulator used only by the Netty event pipeline. */
public final class KrakenTransportTerminationEvidenceAccumulator {
    private String nettyChannelId;
    private boolean channelIdObserved;
    private boolean evidenceObserved;
    private boolean websocketCloseObserved;
    private Integer websocketCloseCode;
    private String websocketCloseReason;
    private boolean channelInactiveObserved;
    private Instant channelInactiveAt;
    private boolean inputShutdownObserved;
    private boolean tlsCloseObserved;
    private Boolean tlsCloseSucceeded;
    private String tlsExceptionClass;
    private boolean transportExceptionObserved;
    private String transportExceptionClass;
    private String transportRootCauseClass;
    private boolean finalized;

    public synchronized void channelId(String value) {
        if (finalized) return;
        if (!channelIdObserved && value != null) {
            nettyChannelId = KrakenStreamTerminationContext.sanitize(value, 128);
            channelIdObserved = true;
            evidenceObserved = true;
        }
    }

    public synchronized void websocketClose(int code, String reason) {
        if (finalized) return;
        if (!websocketCloseObserved) {
            evidenceObserved = true;
            websocketCloseObserved = true;
            websocketCloseCode = code;
            websocketCloseReason = KrakenStreamTerminationContext.sanitize(reason);
        }
    }

    public synchronized void channelInactive(Instant at) {
        if (finalized) return;
        evidenceObserved = true;
        channelInactiveObserved = true;
        if (channelInactiveAt == null) channelInactiveAt = at;
    }

    public synchronized void inputShutdown() {
        if (finalized) return;
        evidenceObserved = true;
        inputShutdownObserved = true;
    }

    public synchronized void tlsClose(boolean success, Throwable error) {
        if (finalized) return;
        evidenceObserved = true;
        tlsCloseObserved = true;
        if (tlsCloseSucceeded == null || !success) tlsCloseSucceeded = success;
        if (error != null && tlsExceptionClass == null) {
            tlsExceptionClass = error.getClass().getName();
        }
    }

    public synchronized void transportException(Throwable error) {
        if (finalized) return;
        evidenceObserved = true;
        transportExceptionObserved = true;
        if (error != null && transportExceptionClass == null) {
            transportExceptionClass = error.getClass().getName();
            Throwable root = error;
            while (root.getCause() != null) root = root.getCause();
            transportRootCauseClass = root.getClass().getName();
        }
    }

    public synchronized KrakenTransportTerminationEvidence snapshot() {
        return snapshotInternal();
    }

    public synchronized KrakenTransportTerminationEvidence snapshotAndFinalize() {
        KrakenTransportTerminationEvidence snapshot = snapshotInternal();
        finalized = true;
        return snapshot;
    }

    private KrakenTransportTerminationEvidence snapshotInternal() {
        return new KrakenTransportTerminationEvidence(
                nettyChannelId,
                evidenceObserved ? websocketCloseObserved : null,
                websocketCloseCode,
                websocketCloseReason,
                evidenceObserved ? channelInactiveObserved : null,
                channelInactiveAt,
                inputShutdownObserved,
                tlsCloseObserved,
                tlsCloseSucceeded,
                tlsExceptionClass,
                transportExceptionObserved,
                transportExceptionClass,
                transportRootCauseClass);
    }
}
