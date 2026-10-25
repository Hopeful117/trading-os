package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketThesisQueryService {
    private final MarketThesisRepository repository;

    public MarketThesisQueryService(MarketThesisRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<MarketThesis> find(UUID thesisId, long version) {
        return repository.findByIdAndVersion(thesisId, version);
    }

    public Optional<MarketThesis> findLatest(UUID marketId, String instrument, String direction) {
        return repository.findLatest(marketId, instrument, direction);
    }
}
