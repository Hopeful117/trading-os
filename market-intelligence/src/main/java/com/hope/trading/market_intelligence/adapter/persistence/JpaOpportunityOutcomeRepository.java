package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.OpportunityOutcomeRepository;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcome;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeIdentity;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaOpportunityOutcomeRepository implements OpportunityOutcomeRepository {
    private final SpringDataOpportunityOutcomeRepository repository;

    public JpaOpportunityOutcomeRepository(SpringDataOpportunityOutcomeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpportunityOutcome> findByFingerprint(String fingerprint) {
        return repository.findByOutcomeFingerprint(fingerprint).map(JpaOpportunityOutcomeEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpportunityOutcome> findByIdentity(OpportunityOutcomeIdentity identity) {
        return repository.findByOutcomeFingerprint(identity.outcomeFingerprint())
                .map(JpaOpportunityOutcomeEntity::toDomain)
                .filter(identity::matches);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpportunityOutcome> findByOpportunity(OpportunityId opportunityId,
                                                     OpportunityVersion opportunityVersion) {
        return repository.findByOpportunityIdAndOpportunityVersion(
                        opportunityId.value(), opportunityVersion.value()).stream()
                .map(JpaOpportunityOutcomeEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpportunityOutcome> findByStatusAndComputedBefore(OpportunityOutcomeStatus status,
                                                                   Instant computedBefore, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return repository.findByStatusAndComputedAtBeforeOrderByComputedAtAsc(
                        status.name(), computedBefore,
                        org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(JpaOpportunityOutcomeEntity::toDomain).toList();
    }

    @Override
    public synchronized OpportunityOutcome saveIfAbsent(OpportunityOutcome outcome) {
        Optional<JpaOpportunityOutcomeEntity> existing =
                repository.findByOutcomeFingerprint(outcome.outcomeFingerprint());
        if (existing.isPresent()) {
            if (!existing.get().hasSameSemanticContent(outcome)) {
                throw new IllegalStateException("Opportunity outcome fingerprint has conflicting content");
            }
            return existing.get().toDomain();
        }
        try {
            return repository.saveAndFlush(JpaOpportunityOutcomeEntity.from(outcome)).toDomain();
        } catch (DataIntegrityViolationException race) {
            return repository.findByOutcomeFingerprint(outcome.outcomeFingerprint())
                    .filter(entity -> entity.hasSameSemanticContent(outcome))
                    .map(JpaOpportunityOutcomeEntity::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent opportunity outcome insert has conflicting content", race));
        }
    }
}
