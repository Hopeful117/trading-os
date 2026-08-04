package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
public class JpaTradingOpportunityRepository implements TradingOpportunityRepository {
    private final SpringDataTradingOpportunityRepository repository;
    private final ObjectMapper mapper;
    private final TradingOpportunityMapper domainMapper = new TradingOpportunityMapper();

    public JpaTradingOpportunityRepository(
            SpringDataTradingOpportunityRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }

    @Override @Transactional
    public synchronized TradingOpportunity append(TradingOpportunity value) {
        long expected = repository.findFirstByOpportunityIdOrderByVersionDesc(value.id().value())
                .map(item -> item.version + 1).orElse(1L);
        if (value.version().value() != expected) {
            throw new IllegalStateException("Opportunity versions must be appended sequentially");
        }
        repository.saveAndFlush(entity(value)); return value;
    }
    @Override @Transactional(readOnly = true)
    public Optional<TradingOpportunity> find(OpportunityId id, OpportunityVersion version) {
        return repository.findById(new JpaTradingOpportunityId(id.value(), version.value())).map(this::domain);
    }
    @Override @Transactional(readOnly = true)
    public Optional<TradingOpportunity> findLatest(OpportunityId id) {
        return repository.findFirstByOpportunityIdOrderByVersionDesc(id.value()).map(this::domain);
    }
    @Override public List<TradingOpportunity> findActive() {
        return findAllLatest().stream().filter(v -> v.status() == OpportunityStatus.ACTIVE).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<TradingOpportunity> findHistory(OpportunityId id) {
        return repository.findByOpportunityIdOrderByVersionAsc(id.value()).stream().map(this::domain).toList();
    }
    @Override public List<TradingOpportunity> findEquivalentCandidates(
            String instrument, OpportunityDirection direction, String scenario,
            String timeframe, Instant evaluatedAfter) {
        return findAllLatest().stream().filter(v -> v.instrument().equalsIgnoreCase(instrument))
                .filter(v -> v.direction() == direction)
                .filter(v -> v.scenario().equalsIgnoreCase(scenario))
                .filter(v -> v.timeframe().equalsIgnoreCase(timeframe))
                .filter(v -> !v.evaluatedAt().isBefore(evaluatedAfter)).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<TradingOpportunity> findAllLatest() {
        Map<UUID, JpaTradingOpportunityEntity> latest = new HashMap<>();
        repository.findAll().forEach(value -> latest.merge(value.opportunityId, value,
                (left, right) -> left.version > right.version ? left : right));
        return latest.values().stream().map(this::domain)
                .sorted(Comparator.comparing(TradingOpportunity::createdAt)
                        .thenComparing(v -> v.id().value())).toList();
    }

    private JpaTradingOpportunityEntity entity(TradingOpportunity value) {
        JpaTradingOpportunityEntity entity = new JpaTradingOpportunityEntity();
        entity.opportunityId = value.id().value(); entity.version = value.version().value();
        entity.status = value.status().name(); entity.instrument = value.instrument();
        entity.direction = value.direction().name(); entity.scenario = value.scenario();
        entity.timeframe = value.timeframe(); entity.evaluatedAt = value.evaluatedAt();
        try { entity.payload = mapper.writeValueAsString(domainMapper.toEntity(value)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize Opportunity", e); }
        return entity;
    }
    private TradingOpportunity domain(JpaTradingOpportunityEntity entity) {
        try { return domainMapper.toDomain(mapper.readValue(entity.payload, TradingOpportunityEntity.class)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize Opportunity", e); }
    }
}
