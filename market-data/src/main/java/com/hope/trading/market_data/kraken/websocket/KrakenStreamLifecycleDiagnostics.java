package com.hope.trading.market_data.kraken.websocket;

import com.hope.trading.market_data.service.OrderBookEvidenceCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Bounded lifecycle logging for diagnosing provider stream termination. */
@Component
public class KrakenStreamLifecycleDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(KrakenStreamLifecycleDiagnostics.class);

    public void connecting(UUID connectionId, String product) {
        log.info("STREAM_CONNECTING provider=KRAKEN product={} connectionId={}", product, connectionId);
    }

    public void connected(UUID connectionId, String product) {
        log.info("STREAM_CONNECTED provider=KRAKEN product={} connectionId={}", product, connectionId);
    }

    public void subscriptionRequested(UUID connectionId, String product, String channel,
                                      List<String> symbols, Integer depth) {
        log.info("SUBSCRIPTION_REQUESTED provider=KRAKEN product={} channel={} symbols={} depth={} connectionId={}",
                product, channel, symbols, depth, connectionId);
    }

    public void subscriptionConfirmed(UUID connectionId, String product, String method) {
        log.info("SUBSCRIPTION_CONFIRMED provider=KRAKEN product={} method={} connectionId={}",
                product, method, connectionId);
    }

    public void desiredSubscriptionRestoreStarted(String provider, String product, String symbol,
                                                   String providerSymbol, String depth) {
        log.info("ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_STARTED provider={} product={} symbol={} "
                        + "providerSymbol={} depth={}",
                provider, product, symbol, providerSymbol, depth);
    }

    public void desiredSubscriptionRestored(String provider, String product, String symbol,
                                             String providerSymbol, String depth) {
        log.info("ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORED provider={} product={} symbol={} "
                        + "providerSymbol={} depth={}",
                provider, product, symbol, providerSymbol, depth);
    }

    public void desiredSubscriptionRestoreFailed(String provider, String product, String symbol,
                                                  String providerSymbol, String depth, String phase,
                                                  Throwable error) {
        log.warn("ORDER_BOOK_DESIRED_SUBSCRIPTION_RESTORE_FAILED provider={} product={} symbol={} "
                        + "providerSymbol={} depth={} phase={} exceptionClass={} exceptionMessage={}",
                provider, product, symbol, providerSymbol, depth, phase,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage());
    }

    public void snapshotAccepted(UUID connectionId, OrderBookEvidenceCaptureService.SequenceDiagnostics sequence) {
        if (sequence == null) return;
        log.info("ORDER_BOOK_SNAPSHOT_ACCEPTED provider=KRAKEN marketId={} depth={} sequenceId={} "
                        + "providerSequence={} localOrdinal={} occurredAt={} knownAt={} connectionId={}",
                sequence.marketId(), sequence.depth(), sequence.sequenceId(), sequence.providerSequence(),
                sequence.localOrdinal(), sequence.lastOccurredAt(), sequence.lastKnownAt(), connectionId);
    }

    public void terminating(UUID connectionId, String product, SignalType signal, Throwable error,
                            List<OrderBookEvidenceCaptureService.SequenceDiagnostics> sequences) {
        if (sequences.isEmpty()) {
            logTermination(connectionId, product, signal, error, null);
            return;
        }
        sequences.forEach(sequence -> logTermination(connectionId, product, signal, error, sequence));
    }

    public void cleanupCompleted(UUID connectionId, String product) {
        log.info("STREAM_CLEANUP_COMPLETED provider=KRAKEN product={} connectionId={}", product, connectionId);
    }

    private void logTermination(UUID connectionId, String product, SignalType signal, Throwable error,
                                OrderBookEvidenceCaptureService.SequenceDiagnostics sequence) {
        TerminationContext context = terminationContext(signal, error, sequence);
        log.warn("STREAM_TERMINATING provider=KRAKEN product={} signalType={} exceptionClass={} "
                        + "exceptionMessage={} rootCauseClass={} rootCauseMessage={} connectionId={} "
                        + "marketId={} depth={} sequenceId={} lastProviderSequence={} lastLocalOrdinal={} "
                        + "lastOccurredAt={} lastKnownAt={} occurredToKnownLag={}",
                product, context.signalType(), context.exceptionClass(), context.exceptionMessage(),
                context.rootCauseClass(), context.rootCauseMessage(),
                connectionId,
                sequence == null ? null : sequence.marketId(),
                sequence == null ? null : sequence.depth(),
                sequence == null ? null : sequence.sequenceId(),
                sequence == null ? null : sequence.providerSequence(),
                sequence == null ? null : sequence.localOrdinal(),
                sequence == null ? null : sequence.lastOccurredAt(),
                sequence == null ? null : sequence.lastKnownAt(), context.occurredToKnownLag());
    }

    static TerminationContext terminationContext(
            SignalType signal,
            Throwable error,
            OrderBookEvidenceCaptureService.SequenceDiagnostics sequence
    ) {
        Throwable rootCause = rootCause(error);
        Duration lag = sequence == null || sequence.lastOccurredAt() == null || sequence.lastKnownAt() == null
                ? null : Duration.between(sequence.lastOccurredAt(), sequence.lastKnownAt());
        return new TerminationContext(signal, className(error), message(error), className(rootCause),
                message(rootCause), lag);
    }

    private static String className(Throwable error) {
        return error == null ? null : error.getClass().getName();
    }

    private static String message(Throwable error) {
        return error == null ? null : error.getMessage();
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current;
    }

    record TerminationContext(
            SignalType signalType,
            String exceptionClass,
            String exceptionMessage,
            String rootCauseClass,
            String rootCauseMessage,
            Duration occurredToKnownLag
    ) {
    }
}
