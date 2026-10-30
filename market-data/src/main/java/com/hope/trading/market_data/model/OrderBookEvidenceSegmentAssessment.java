package com.hope.trading.market_data.model;

import java.util.List;

public record OrderBookEvidenceSegmentAssessment(
        OrderBookEvidenceSegment segment,
        boolean eligible,
        long evidenceCount,
        List<OrderBookSegmentBlocker> blockers
) {
    public OrderBookEvidenceSegmentAssessment {
        blockers = List.copyOf(blockers);
    }
}
