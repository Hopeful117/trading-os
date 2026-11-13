package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.ResearchHypothesisRepository;
import com.hope.trading.market_intelligence.domain.research.ResearchHypothesis;
import org.springframework.stereotype.Repository;

@Repository
public class JpaResearchHypothesisRepository implements ResearchHypothesisRepository {
    private final SpringDataResearchHypothesisRepository repository;
    private final ObjectMapper mapper;

    public JpaResearchHypothesisRepository(SpringDataResearchHypothesisRepository repository,
                                           ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ResearchHypothesis save(ResearchHypothesis hypothesis) {
        if (repository.existsByHypothesisIdAndVersion(hypothesis.hypothesisId(), hypothesis.version())) {
            throw new IllegalStateException("Research hypothesis version already exists: "
                    + hypothesis.hypothesisId() + ":" + hypothesis.version());
        }
        JpaResearchHypothesisEntity entity = new JpaResearchHypothesisEntity();
        entity.hypothesisId = hypothesis.hypothesisId();
        entity.version = hypothesis.version();
        entity.researchId = hypothesis.researchId();
        entity.marketId = hypothesis.marketId();
        entity.contextFingerprint = hypothesis.contextFingerprint();
        try {
            entity.proposal = mapper.writeValueAsString(hypothesis.proposal());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize research hypothesis", exception);
        }
        entity.validationStatus = hypothesis.validationStatus();
        entity.createdAt = hypothesis.createdAt();
        repository.saveAndFlush(entity);
        return hypothesis;
    }
}
