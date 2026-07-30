package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.*;

public final class DefaultOpportunityRegistry implements OpportunityRegistry {
    private final TradingOpportunityRepository repository;
    private final OpportunityEngine engine;

    public DefaultOpportunityRegistry(
            TradingOpportunityRepository repository, OpportunityEngine engine) {
        this.repository = Objects.requireNonNull(repository);
        this.engine = Objects.requireNonNull(engine);
    }
    @Override public List<TradingOpportunity> active() { return repository.findActive(); }
    @Override public Optional<TradingOpportunity> latest(OpportunityId id) {
        return repository.findLatest(id);
    }
    @Override public List<TradingOpportunity> history(OpportunityId id) {
        return repository.findHistory(id);
    }
    @Override public TradingOpportunity transition(
            OpportunityId id, OpportunityStatus target) {
        return engine.transition(id, target);
    }
    @Override public List<TradingOpportunity> expireDue(
            OpportunityExpirationPolicy policy, java.time.Instant at) {
        return repository.findAllLatest().stream()
                .filter(item -> item.status() == OpportunityStatus.DETECTED
                        || item.status() == OpportunityStatus.ANALYZED
                        || item.status() == OpportunityStatus.ACTIVE)
                .filter(item -> policy.evaluate(item, at).expire())
                .map(item -> engine.transition(item.id(), OpportunityStatus.EXPIRED))
                .toList();
    }
}
