package com.hope.trading.market_data.service;

import com.hope.trading.market_data.adapter.persistence.JpaHistoricalOhlcEvidenceRepository;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcEvidenceOrigin;
import com.hope.trading.market_data.model.OhlcEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HistoricalOhlcEvidenceWriter {
    private final JpaHistoricalOhlcEvidenceRepository repository;
    private final Clock clock;

    public HistoricalOhlcEvidenceWriter(JpaHistoricalOhlcEvidenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<HistoricalOhlcEvidence> appendCaptured(List<OhlcEvent> events) {
        return events == null ? List.of() : events.stream()
                .map(this::appendCaptured)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<HistoricalOhlcEvidence> appendCaptured(OhlcEvent event) {
        if (event == null || !event.closed() || event.synthetic()) {
            return Optional.empty();
        }
        Instant capturedAt = clock.instant();
        Instant knownAt = capturedAt.isAfter(event.fetchedAt()) ? capturedAt : event.fetchedAt();
        return append(event, HistoricalOhlcEvidenceOrigin.CAPTURED, capturedAt, knownAt);
    }

    public Optional<HistoricalOhlcEvidence> append(
            OhlcEvent event,
            HistoricalOhlcEvidenceOrigin origin,
            Instant capturedAt,
            Instant knownAt
    ) {
        if (event == null || !event.closed() || event.synthetic()) {
            return Optional.empty();
        }
        if (origin == null || capturedAt == null || knownAt == null) {
            throw new IllegalArgumentException("origin, capturedAt and knownAt are required");
        }
        String fingerprint = HistoricalOhlcEvidenceFingerprint.fingerprint(
                event, origin, HistoricalOhlcEvidenceFingerprint.NORMALIZATION_VERSION);
        HistoricalOhlcEvidence evidence = new HistoricalOhlcEvidence(
                UUID.randomUUID(), event.marketId(), event.interval(), event.openTime(), event.closeTime(),
                event.open(), event.high(), event.low(), event.close(), event.volume(), event.vwap(),
                 event.trades(), event.closed(), event.synthetic(), event.provider(), event.providerSymbol(),
                event.sourceId(), null, event.fetchedAt(), capturedAt, knownAt, origin,
                HistoricalOhlcEvidenceFingerprint.NORMALIZATION_VERSION, fingerprint);
        return repository.append(evidence);
    }
}
