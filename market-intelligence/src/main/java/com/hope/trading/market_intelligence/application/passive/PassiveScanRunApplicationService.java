package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.port.PassiveDispatchCapacityRepository;
import com.hope.trading.market_intelligence.config.PassiveDispatchProperties;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarket;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PassiveScanRunApplicationService {
    private final PassiveScanRunRepository repository;
    private final PassiveScanCreationTransaction creation;
    private final PassiveScanClaimService claims;
    private final PassiveScanReconciliationService reconciliation;
    private final AnalysisExecutionService executions;
    private final PassiveDispatchCapacityRepository capacity;
    private final PassiveDispatchProperties dispatchProperties;
    private final PassiveDispatchMetrics metrics;
    private final Clock clock;

    public PassiveScanRunApplicationService(PassiveScanRunRepository repository,
                                             PassiveScanCreationTransaction creation,
                                             PassiveScanClaimService claims,
                                             PassiveScanReconciliationService reconciliation,
                                             AnalysisExecutionService executions,
                                             PassiveDispatchCapacityRepository capacity,
                                             PassiveDispatchProperties dispatchProperties,
                                             PassiveDispatchMetrics metrics,
                                             Clock clock) {
        this.repository = repository;
        this.creation = creation;
        this.claims = claims;
        this.reconciliation = reconciliation;
        this.executions = executions;
        this.capacity = capacity;
        this.dispatchProperties = dispatchProperties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public PassiveScanRun create(PassiveScanRunRequest request, PassiveMarketUniverse universe) {
        String fingerprint = PassiveScanFingerprintFactory.requestFingerprint(request, universe);
        return repository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> {
                    if (!existing.requestFingerprint().equals(fingerprint)) {
                        throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
                    }
                    return existing;
                })
                .orElseGet(() -> createIdempotently(request, universe, fingerprint));
    }

    private PassiveScanRun createIdempotently(PassiveScanRunRequest request, PassiveMarketUniverse universe,
                                               String fingerprint) {
        try {
            return creation.create(request, universe, fingerprint);
        } catch (DataIntegrityViolationException race) {
            return repository.findByIdempotencyKey(request.idempotencyKey())
                    .filter(existing -> existing.requestFingerprint().equals(fingerprint))
                    .orElseThrow(() -> race);
        }
    }

    public UUID claim(UUID runId, UUID scanMarketId) { return claims.claim(runId, scanMarketId); }

    public boolean recover(UUID runId, UUID scanMarketId) { return claims.recover(runId, scanMarketId); }

    public int recoverOrphanedCapacity() { return claims.recoverOrphanedCapacity(); }

    @Transactional
    public boolean recordOutcome(UUID scanMarketId, PassiveScanMarketStatus expected) {
        PassiveScanMarket market = repository.findMarketById(scanMarketId).orElseThrow();
        AnalysisExecution execution = executions.find(market.analysisExecutionId());
        PassiveScanMarketStatus target = outcomeFor(execution);
        String failureCode = execution.failureCode().orElse(null);
        String failureReason = target == PassiveScanMarketStatus.INSUFFICIENT_EVIDENCE
                ? "Analysis completed without complete evidence" : failureCode;
        Instant now = clock.instant();
        Instant nextRetryAt = target == PassiveScanMarketStatus.FAILED && isRetryable(failureCode)
                && market.attemptCount() < dispatchProperties.getMaxAttempts()
                ? now.plus(retryDelay(market.cadence(), market.attemptCount())) : null;
        boolean recorded = repository.recordMarketOutcome(scanMarketId, expected, market.leaseId(), market.version(),
                target, failureCode, failureReason, market.attemptCount(), nextRetryAt, now);
        if (recorded) {
            capacity.release(market.leaseId(), now);
            if (nextRetryAt != null) metrics.retry();
            if (target == PassiveScanMarketStatus.INSUFFICIENT_EVIDENCE) metrics.insufficientEvidence();
        }
        return recorded;
    }

    private Duration retryDelay(Duration cadence, int attemptCount) {
        long multiplier = 1L << Math.min(10, Math.max(0, attemptCount - 1));
        Duration candidate;
        try { candidate = cadence.multipliedBy(multiplier); }
        catch (ArithmeticException ignored) { candidate = dispatchProperties.getRetryMaxDelay(); }
        return candidate.compareTo(dispatchProperties.getRetryMaxDelay()) > 0
                ? dispatchProperties.getRetryMaxDelay() : candidate;
    }

    private boolean isRetryable(String failureCode) {
        return "DISPATCH_FAILED".equals(failureCode)
                || "CONTEXT_UNAVAILABLE".equals(failureCode)
                || "CONTEXT_STALE".equals(failureCode)
                || "MARKET_SNAPSHOT_UNAVAILABLE".equals(failureCode)
                || "MARKET_SNAPSHOT_STALE".equals(failureCode);
    }

    public PassiveScanRun reconcile(UUID runId) { return reconciliation.reconcile(runId); }

    public PassiveScanRun find(UUID runId) {
        return repository.findById(runId).orElseThrow(() -> new IllegalArgumentException("Unknown passive scan run: " + runId));
    }

    private PassiveScanMarketStatus outcomeFor(AnalysisExecution execution) {
        if (execution.status() == AnalysisExecutionStatus.COMPLETED) {
            return execution.resultQuality().orElse(null) == AnalysisResultQuality.COMPLETE
                    ? PassiveScanMarketStatus.COMPLETED : PassiveScanMarketStatus.INSUFFICIENT_EVIDENCE;
        }
        if (execution.status() == AnalysisExecutionStatus.PARTIALLY_COMPLETED) {
            return PassiveScanMarketStatus.INSUFFICIENT_EVIDENCE;
        }
        if (execution.status() == AnalysisExecutionStatus.FAILED
                || execution.status() == AnalysisExecutionStatus.CANCELLED
                || execution.status() == AnalysisExecutionStatus.EXPIRED) {
            return PassiveScanMarketStatus.FAILED;
        }
        throw new IllegalStateException("Analysis execution is not terminal: " + execution.status());
    }

    public PassiveScanRunProjection readProjection(UUID runId) {
        PassiveScanRun run = find(runId);
        List<PassiveScanMarket> markets = repository.findMarketsByRunId(runId);
        Map<PassiveScanMarketStatus, Integer> counts = new EnumMap<>(PassiveScanMarketStatus.class);
        markets.forEach(market -> counts.merge(market.status(), 1, Integer::sum));
        List<PassiveScanRunProjection.MarketSummary> summaries = markets.stream()
                .map(market -> new PassiveScanRunProjection.MarketSummary(
                        market.scanMarketId(), market.marketId(), market.provider(), market.productType(),
                        market.status(), market.analysisExecutionId(), market.failureCode()))
                .toList();
        return new PassiveScanRunProjection(run.runId(), run.idempotencyKey(), run.status(),
                markets.size(), Map.copyOf(counts), summaries, run.diagnostics());
    }
}
