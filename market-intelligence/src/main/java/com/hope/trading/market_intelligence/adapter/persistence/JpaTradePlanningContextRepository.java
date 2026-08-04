package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class JpaTradePlanningContextRepository implements TradePlanningContextRepository {
    private final SpringDataTradePlanningContextRepository repository;
    private final ObjectMapper mapper;

    public JpaTradePlanningContextRepository(
            SpringDataTradePlanningContextRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void saveSnapshot(TradePlanningContext context) {
        String payload = write(new Payload(
                context.accountCurrency(), context.riskBudget(), context.preferences()));
        String fingerprint = ArtifactFingerprint.ofInputs(List.of(payload)).value();
        Optional<JpaTradePlanningContextEntity> existing = repository.findById(
                new JpaTradePlanningContextId(context.id(), context.version()));
        if (existing.isPresent()) {
            if (!existing.get().payloadFingerprint.equals(fingerprint)
                    || !existing.get().ownerId.equals(context.ownerId())
                    || !existing.get().tradingAccountId.equals(context.tradingAccountId())
                    || !existing.get().capturedAt.equals(context.capturedAt())) {
                throw new IllegalStateException("TradePlanningContext snapshot payload conflicts");
            }
            toDomain(existing.get());
            return;
        }
        JpaTradePlanningContextEntity entity = new JpaTradePlanningContextEntity();
        entity.contextId = context.id(); entity.version = context.version();
        entity.ownerId = context.ownerId(); entity.tradingAccountId = context.tradingAccountId();
        entity.capturedAt = context.capturedAt(); entity.payloadFingerprint = fingerprint;
        entity.payload = payload;
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradePlanningContext> find(UUID id, long version) {
        return repository.findById(new JpaTradePlanningContextId(id, version)).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradePlanningContext> findLatest(UUID id) {
        return repository.findFirstByContextIdOrderByVersionDesc(id).map(this::toDomain);
    }

    private TradePlanningContext toDomain(JpaTradePlanningContextEntity entity) {
        Payload payload = read(entity.payload);
        return new TradePlanningContext(
                entity.contextId, entity.version, entity.capturedAt, entity.ownerId,
                entity.tradingAccountId, payload.accountCurrency(), payload.riskBudget(),
                payload.preferences());
    }

    private String write(Payload payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize TradePlanningContext", exception);
        }
    }

    private Payload read(String payload) {
        try { return mapper.readValue(payload, Payload.class); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize TradePlanningContext", exception);
        }
    }

    private record Payload(
            String accountCurrency, RiskBudget riskBudget, PlanningPreferences preferences) { }
}
