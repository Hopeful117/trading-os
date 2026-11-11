package com.hope.trading.market_intelligence.application.research.histdata;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** No approved historical closure source is configured yet. */
@Component
public class UnavailableHistoricalClosureEvidenceSource implements HistoricalClosureEvidenceSource {
    @Override
    public List<Evidence> find(Set<UUID> marketIds, Instant startCutoffInclusive,
                               Instant endCutoffExclusive) {
        return List.of();
    }
}
