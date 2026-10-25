package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketThesisRepository {
    MarketThesis save(MarketThesis thesis);
    MarketThesis saveIfAbsent(MarketThesis thesis);
    Optional<MarketThesis> findById(UUID id);
    Optional<MarketThesis> findByIdAndVersion(UUID id, long version);
    Optional<MarketThesis> findByInputFingerprint(String inputFingerprint);
    Optional<MarketThesis> findLatest(UUID marketId, String instrument, String direction);
    List<MarketThesis> findByMarket(UUID marketId);
}
