package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile;
import com.hope.trading.market_intelligence.domain.passive.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPassiveScanRunRepository implements PassiveScanRunRepository {
    private final SpringDataPassiveScanRunRepository runs;
    private final SpringDataPassiveScanMarketRepository markets;
    private final SpringDataPassiveScanAttemptRepository attempts;
    private final ObjectMapper mapper;

    public JpaPassiveScanRunRepository(SpringDataPassiveScanRunRepository runs,
                                       SpringDataPassiveScanMarketRepository markets,
                                       SpringDataPassiveScanAttemptRepository attempts,
                                       ObjectMapper mapper) {
        this.runs = runs;
        this.markets = markets;
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PassiveScanRun saveRun(PassiveScanRun value) {
        JpaPassiveScanRunEntity entity = new JpaPassiveScanRunEntity();
        entity.runId = value.runId();
        entity.idempotencyKey = value.idempotencyKey();
        entity.requestFingerprint = value.requestFingerprint();
        entity.triggerContext = value.triggerContext();
        entity.plannedWindowStart = value.plannedWindowStart();
        entity.plannedWindowEnd = value.plannedWindowEnd();
        entity.configurationVersion = value.configurationVersion();
        entity.fingerprintAlgorithm = value.fingerprintAlgorithm();
        entity.universeFingerprint = value.universeFingerprint();
        entity.resolvedAt = value.resolvedAt();
        entity.status = value.status().name();
        entity.diagnostics = write(value.diagnostics());
        entity.terminalSummary = value.status().isTerminal() ? "total=0" : "";
        entity.completedAt = value.status().isTerminal() ? value.updatedAt() : null;
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        entity.version = 0;
        runs.saveAndFlush(entity);
        return value;
    }

    @Override
    @Transactional
    public List<PassiveScanMarket> saveMarkets(List<PassiveScanMarket> values) {
        markets.saveAllAndFlush(values.stream().map(this::toEntity).toList());
        return values;
    }

    @Override
    @Transactional
    public void saveAttempt(UUID attemptId, UUID scanMarketId, int attemptNumber,
                            UUID analysisExecutionId, String idempotencyKey, Instant createdAt) {
        JpaPassiveScanAttemptEntity entity = new JpaPassiveScanAttemptEntity();
        entity.attemptId = attemptId;
        entity.scanMarketId = scanMarketId;
        entity.attemptNumber = attemptNumber;
        entity.analysisExecutionId = analysisExecutionId;
        entity.idempotencyKey = idempotencyKey;
        entity.createdAt = createdAt;
        attempts.saveAndFlush(entity);
    }

    @Override @Transactional(readOnly = true)
    public Optional<PassiveScanRun> findById(UUID runId) { return runs.findById(runId).map(this::toDomain); }

    @Override @Transactional(readOnly = true)
    public Optional<PassiveScanRun> findByIdempotencyKey(String key) { return runs.findByIdempotencyKey(key).map(this::toDomain); }

    @Override @Transactional(readOnly = true)
    public List<PassiveScanMarket> findMarketsByRunId(UUID runId) {
        return markets.findByRunIdOrderByOrdinalAsc(runId).stream().map(this::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<PassiveScanMarket> findMarketById(UUID id) { return markets.findById(id).map(this::toDomain); }

    @Override @Transactional(readOnly = true)
    public Optional<PassiveScanMarket> findLatestMarketByMarketId(UUID marketId) {
        return markets.findFirstByMarketIdOrderByUpdatedAtDesc(marketId).map(this::toDomain);
    }

    @Override @Transactional(readOnly = true)
    public long countActiveMarkets() { return markets.countActive(); }

    @Override @Transactional(readOnly = true)
    public List<PassiveScanMarket> findActiveMarkets() {
        return markets.findActive().stream().map(this::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public long countFailedAttempts(UUID marketId) { return markets.countFailedAttempts(marketId); }

    @Override @Transactional
    public boolean transitionRunStatus(UUID id, PassiveScanRunStatus expected, PassiveScanRunStatus target,
                                       String summary, Instant at) {
        return runs.transitionStatus(id, expected.name(), target.name(), summary, at) == 1;
    }

    @Override @Transactional
    public boolean transitionMarketStatus(UUID id, PassiveScanMarketStatus expected, PassiveScanMarketStatus target,
                                         long expectedVersion, Instant at) {
        return markets.transitionStatus(id, expected.name(), target.name(), expectedVersion, at) == 1;
    }

    @Override @Transactional
    public boolean claimMarket(UUID id, PassiveScanMarketStatus expected, long expectedVersion,
                               UUID leaseId, Instant expiresAt, Instant at) {
        return markets.claim(id, expected.name(), PassiveScanMarketStatus.DISPATCH_REQUESTED.name(),
                expectedVersion, leaseId, expiresAt, at) == 1;
    }

    @Override @Transactional
    public boolean recordMarketOutcome(UUID id, PassiveScanMarketStatus expected, UUID leaseId, long expectedVersion,
                                       PassiveScanMarketStatus target, String failureCode, String failureReason,
                                       int attemptCount, Instant nextRetryAt, Instant at) {
        return markets.recordOutcome(id, expected.name(), target.name(), failureCode, failureReason,
                attemptCount, nextRetryAt, leaseId, expectedVersion, at) == 1;
    }

    @Override @Transactional
    public boolean recoverExpiredMarket(UUID id, PassiveScanMarketStatus expected, UUID leaseId,
                                        long expectedVersion, Instant at) {
        return markets.recoverExpired(id, expected.name(), leaseId, expectedVersion, at) == 1;
    }

    private JpaPassiveScanMarketEntity toEntity(PassiveScanMarket value) {
        JpaPassiveScanMarketEntity entity = new JpaPassiveScanMarketEntity();
        entity.scanMarketId = value.scanMarketId();
        entity.runId = value.runId();
        entity.ordinal = value.ordinal();
        entity.marketId = value.marketId();
        entity.provider = value.provider();
        entity.productType = value.productType();
        entity.cadenceSeconds = value.cadence().toSeconds();
        entity.minimumEvidenceProfile = value.minimumEvidenceProfile().name();
        entity.status = value.status().name();
        entity.analysisExecutionId = value.analysisExecutionId();
        entity.attemptCount = value.attemptCount();
        entity.failureCode = value.failureCode();
        entity.failureReason = value.failureReason();
        entity.nextRetryAt = value.nextRetryAt();
        entity.leaseId = value.leaseId();
        entity.leaseExpiresAt = value.leaseExpiresAt();
        entity.dispatchRequestedAt = value.status() == PassiveScanMarketStatus.DISPATCH_REQUESTED ? value.updatedAt() : null;
        entity.acceptedAt = value.status() == PassiveScanMarketStatus.ACCEPTED ? value.updatedAt() : null;
        entity.startedAt = value.status() == PassiveScanMarketStatus.RUNNING ? value.updatedAt() : null;
        entity.completedAt = value.status().isTerminal() ? value.updatedAt() : null;
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        entity.version = 0;
        return entity;
    }

    private PassiveScanRun toDomain(JpaPassiveScanRunEntity entity) {
        return PassiveScanRun.restore(entity.runId, entity.idempotencyKey, entity.requestFingerprint,
                entity.triggerContext, entity.plannedWindowStart, entity.plannedWindowEnd,
                entity.configurationVersion, entity.fingerprintAlgorithm, entity.universeFingerprint,
                entity.resolvedAt, PassiveScanRunStatus.valueOf(entity.status), read(entity.diagnostics),
                entity.createdAt, entity.updatedAt);
    }

    private PassiveScanMarket toDomain(JpaPassiveScanMarketEntity entity) {
        return PassiveScanMarket.restore(entity.scanMarketId, entity.runId, entity.ordinal, entity.marketId,
                entity.provider, entity.productType, entity.cadence(),
                TradeabilityEvidenceProfile.valueOf(entity.minimumEvidenceProfile),
                PassiveScanMarketStatus.valueOf(entity.status), entity.analysisExecutionId,
                entity.attemptCount, entity.failureCode, entity.failureReason, entity.nextRetryAt,
                entity.leaseId, entity.leaseExpiresAt, entity.version, entity.createdAt, entity.updatedAt);
    }

    private String write(List<String> values) {
        try { return mapper.writeValueAsString(values); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize passive scan diagnostics", e); }
    }

    private List<String> read(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot deserialize passive scan diagnostics", e); }
    }
}
