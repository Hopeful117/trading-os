package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class MarketSnapshotContextContributor implements ContextContributor {
    private final MarketDataClient marketDataClient;
    private final MarketDataSectionFactory sectionFactory;

    public MarketSnapshotContextContributor(
            MarketDataClient marketDataClient,
            MarketDataSectionFactory sectionFactory
    ) {
        this.marketDataClient = marketDataClient;
        this.sectionFactory = sectionFactory;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.MARKET_SNAPSHOT;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        MarketPriceSnapshotResponse snapshot = marketDataClient.findPriceSnapshots(
                new MarketPriceSnapshotRequest(
                        List.of(request.marketId()),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(18),
                        "CONFIRM_OR_ACQUIRE",
                        request.evaluationCutoff()
                )
        ).stream().findFirst().orElse(null);
        ContextRequirement requirement =
                ContextRequirement.requiredPublic(sectionType());
        if (snapshot == null) {
            return ContextSection.unavailable(requirement, "Current market snapshot is unavailable");
        }
        if ("UNAVAILABLE".equals(snapshot.status())) {
            return ContextSection.unavailable(requirement, "Current market snapshot is unavailable");
        }
        if ("UNKNOWN_MARKET".equals(snapshot.status())) {
            return ContextSection.unavailable(requirement, "Requested market is unknown to Market Data");
        }
        MarketSnapshotContext payload = new MarketSnapshotContext(
                snapshot.marketId(),
                snapshot.symbol(),
                snapshot.lastPrice(),
                snapshot.bid(),
                snapshot.ask(),
                snapshot.tradable(),
                snapshot.occurredAt()
        );
        // A live active scan acquires its snapshot during context construction, so
        // its capture time is necessarily after the dispatch cutoff.
        ContextSection section = request.evidencePolicy() == EvidenceAcquisitionPolicy.LIVE_CURRENT
                && request.scanId() != null
                ? sectionFactory.snapshot(
                payload,
                snapshot.status(),
                snapshot.occurredAt(),
                snapshot.capturedAt())
                : sectionFactory.snapshotAt(
                payload,
                snapshot.status(),
                snapshot.occurredAt(),
                snapshot.capturedAt(),
                request.evaluationCutoff()
        );
        if (section.provenance() == null) {
            return section;
        }
        return new ContextSection(
                section.type(), section.status(), section.sensitivity(), section.payload(),
                new ContextProvenance(
                        section.provenance().source(), section.provenance().sourceOccurredAt(),
                        section.provenance().fetchedAt(), snapshot.sourceSnapshotId(),
                         snapshot.sourceSnapshotVersion() == null
                                 ? null : snapshot.sourceSnapshotVersion().toString(), null,
                        request.evidencePolicy(), null, null, request.evaluationCutoff()),
                section.message());
    }
}
