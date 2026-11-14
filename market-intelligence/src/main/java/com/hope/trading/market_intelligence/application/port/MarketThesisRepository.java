package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Collection;

public interface MarketThesisRepository {
    MarketThesis save(MarketThesis thesis);
    MarketThesis saveIfAbsent(MarketThesis thesis);
    Optional<MarketThesis> findById(UUID id);
    Optional<MarketThesis> findByIdAndVersion(UUID id, long version);
    default List<MarketThesis> findByIdAndVersions(Collection<MarketThesisReference> references) {
        return references.stream()
                .map(reference -> findByIdAndVersion(reference.thesisId(), reference.version()))
                .flatMap(Optional::stream).toList();
    }
    /**
     * Returns the subset of the given thesis IDs that a later thesis names in
     * its {@code supersedes} reference.
     */
    Set<UUID> findSupersededThesisIds(Collection<UUID> thesisIds);
    Optional<MarketThesis> findByInputFingerprint(String inputFingerprint);
    Optional<MarketThesis> findLatest(UUID marketId, String instrument, String direction);
    List<MarketThesis> findByMarket(UUID marketId);
}
