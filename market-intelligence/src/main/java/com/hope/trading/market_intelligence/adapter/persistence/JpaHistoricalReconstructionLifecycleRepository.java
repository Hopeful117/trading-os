package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.HistoricalReconstructionLifecycleRepository;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecyclePhase;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycleStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaHistoricalReconstructionLifecycleRepository
        implements HistoricalReconstructionLifecycleRepository {
    private final SpringDataHistoricalReconstructionLifecycleRepository repository;
    private final TransactionTemplate requiresNew;

    public JpaHistoricalReconstructionLifecycleRepository(
            SpringDataHistoricalReconstructionLifecycleRepository repository,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HistoricalReconstructionLifecycle save(HistoricalReconstructionLifecycle value) {
        JpaHistoricalReconstructionLifecycleEntity entity = repository.findById(value.executionId())
                .orElseGet(JpaHistoricalReconstructionLifecycleEntity::new);
        entity.executionId = value.executionId();
        entity.leaseId = value.leaseId();
        entity.status = value.status().name();
        entity.phase = value.phase().name();
        entity.failureCode = value.failureCode();
        entity.failureReason = value.failureReason();
        entity.snapshotFingerprint = value.snapshotFingerprint();
        entity.outcomeFingerprint = value.outcomeFingerprint();
        entity.outputDirectory = value.outputDirectory();
        entity.startedAt = value.startedAt();
        entity.updatedAt = value.updatedAt();
        entity.completedAt = value.completedAt();
        repository.saveAndFlush(entity);
        return value;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveOwned(HistoricalReconstructionLifecycle value, UUID leaseId) {
        JpaHistoricalReconstructionLifecycleEntity entity = repository.findForUpdate(value.executionId()).orElse(null);
        if (entity == null || !leaseId.equals(entity.leaseId)) return false;
        entity.leaseId = value.leaseId();
        entity.status = value.status().name();
        entity.phase = value.phase().name();
        entity.failureCode = value.failureCode();
        entity.failureReason = value.failureReason();
        entity.snapshotFingerprint = value.snapshotFingerprint();
        entity.outcomeFingerprint = value.outcomeFingerprint();
        entity.outputDirectory = value.outputDirectory();
        entity.startedAt = value.startedAt();
        entity.updatedAt = value.updatedAt();
        entity.completedAt = value.completedAt();
        repository.saveAndFlush(entity);
        return true;
    }

    @Override
    public boolean saveIfAbsent(HistoricalReconstructionLifecycle value) {
        try {
            return Boolean.TRUE.equals(requiresNew.execute(status -> {
                JpaHistoricalReconstructionLifecycleEntity entity = new JpaHistoricalReconstructionLifecycleEntity();
                entity.executionId = value.executionId();
                entity.leaseId = value.leaseId();
                entity.status = value.status().name();
                entity.phase = value.phase().name();
                entity.outputDirectory = value.outputDirectory();
                entity.startedAt = value.startedAt();
                entity.updatedAt = value.updatedAt();
                repository.saveAndFlush(entity);
                return true;
            }));
        } catch (org.springframework.dao.DataIntegrityViolationException alreadyExists) {
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimIfStale(UUID executionId, UUID leaseId, java.time.Instant staleBefore,
                                java.time.Instant claimedAt) {
        return repository.claimIfStale(executionId, leaseId, staleBefore, claimedAt) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwned(UUID executionId, UUID leaseId) {
        return repository.findById(executionId)
                .filter(entity -> "RUNNING".equals(entity.status) && leaseId.equals(entity.leaseId))
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HistoricalReconstructionLifecycle> findByExecutionId(UUID executionId) {
        return repository.findById(executionId).map(this::domain);
    }

    private HistoricalReconstructionLifecycle domain(JpaHistoricalReconstructionLifecycleEntity entity) {
        return new HistoricalReconstructionLifecycle(entity.executionId, entity.leaseId,
                HistoricalReconstructionLifecycleStatus.valueOf(entity.status),
                HistoricalReconstructionLifecyclePhase.valueOf(entity.phase), entity.failureCode,
                entity.failureReason, entity.snapshotFingerprint, entity.outcomeFingerprint,
                entity.outputDirectory, entity.startedAt, entity.updatedAt, entity.completedAt);
    }
}
