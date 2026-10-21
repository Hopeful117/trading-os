package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.ContextProvenance;
import com.hope.trading.market_intelligence.domain.ContextRequirement;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.OrderBookContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderBookContextContributor implements ContextContributor {
    static final int BOUNDED_DEPTH = 25;
    private static final String REPLAY_VERSION = "order-book-replay-v1";
    private final OrderBookReplayClient client;

    public OrderBookContextContributor(OrderBookReplayClient client) {
        this.client = client;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.ORDER_BOOK;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        ContextRequirement requirement = ContextRequirement.requiredPublic(sectionType());
        if (request.evidencePolicy() != EvidenceAcquisitionPolicy.STRICT_OBSERVED
                || request.evaluationCutoff() == null) {
            return ContextSection.unsupported(requirement,
                    "Historical Order Book analysis requires strict replay and an evaluation cutoff");
        }
        OrderBookReplayClient.ReplayResponse response = client.replay(
                request.marketId(), BOUNDED_DEPTH, request.evaluationCutoff(),
                OrderBookReplayIdentity.forRequest(request.marketId(), BOUNDED_DEPTH,
                        request.evaluationCutoff()));
        OrderBookContext context = map(request, response);
        if (context.status() == OrderBookContext.ReplayStatus.UNAVAILABLE) {
            return ContextSection.unavailable(requirement, context.reason());
        }
        if (context.status() == OrderBookContext.ReplayStatus.INVALID) {
            return ContextSection.incomplete(requirement, context.reason());
        }
        return new ContextSection(
                sectionType(),
                com.hope.trading.market_intelligence.domain.ContextSectionStatus.AVAILABLE,
                com.hope.trading.market_intelligence.domain.ContextSensitivity.PUBLIC,
                context,
                new ContextProvenance(
                        "market-data", context.latestOccurredAt(), context.latestKnownAt(),
                        context.sequenceId().toString(), REPLAY_VERSION, context.normalizationVersion(),
                        request.evidencePolicy(), context.sourceEvidenceFingerprint(),
                        context.bids().size() + context.asks().size()),
                null);
    }

    private OrderBookContext map(
            IntelligenceAnalysisRequest request,
            OrderBookReplayClient.ReplayResponse response) {
        if (response == null) {
            return unavailable(request, "Order Book replay response is unavailable");
        }
        OrderBookContext.ReplayStatus status;
        try {
            status = OrderBookContext.ReplayStatus.valueOf(response.status());
        } catch (RuntimeException exception) {
            return unavailable(request, "Order Book replay returned an unknown status");
        }
        return new OrderBookContext(
                response.marketId() == null ? request.marketId() : response.marketId(),
                response.depth(), status, response.sequenceId(),
                response.evaluationCutoff() == null ? request.evaluationCutoff() : response.evaluationCutoff(),
                response.latestOccurredAt(), response.latestKnownAt(), response.sourceStartOrdinal(),
                response.sourceEndOrdinal(), response.provider(), response.providerSymbol(),
                response.normalizationVersion(), response.sourceEvidenceFingerprint(),
                response.bids().stream().map(value -> new OrderBookContext.Level(value.price(), value.quantity())).toList(),
                response.asks().stream().map(value -> new OrderBookContext.Level(value.price(), value.quantity())).toList(),
                response.reason());
    }

    private OrderBookContext unavailable(IntelligenceAnalysisRequest request, String reason) {
        return new OrderBookContext(request.marketId(), BOUNDED_DEPTH,
                OrderBookContext.ReplayStatus.UNAVAILABLE, null, request.evaluationCutoff(),
                null, null, null, null, null, null, null, null, List.of(), List.of(), reason);
    }
}
