package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarket;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PassiveScanCreationTransaction {
    private final PassiveScanRunRepository repository;
    private final AnalysisExecutionService executions;
    private final Clock clock;

    public PassiveScanCreationTransaction(PassiveScanRunRepository repository,
                                          AnalysisExecutionService executions,
                                          Clock clock) {
        this.repository = repository;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional
    public PassiveScanRun create(PassiveScanRunRequest request, PassiveMarketUniverse universe,
                                 String requestFingerprint) {
        Instant now = clock.instant();
        List<String> diagnostics = universe.diagnostics().stream()
                .map(PassiveScanFingerprintFactory::diagnostic).toList();
        boolean noWork = universe.entries().isEmpty();
        boolean resolutionFailed = universe.diagnostics().stream()
                .anyMatch(value -> value.severity() == PassiveMarketUniverseDiagnostic.Severity.ERROR);
        PassiveScanRun run = noWork && !resolutionFailed
                ? PassiveScanRun.completedNoWork(UUID.randomUUID(), request.idempotencyKey(), requestFingerprint,
                request.triggerContext(), request.plannedWindowStart(), request.plannedWindowEnd(),
                universe.configurationVersion(), universe.fingerprintAlgorithm(), universe.fingerprint(),
                universe.resolvedAt(), diagnostics, now)
                : PassiveScanRun.requested(UUID.randomUUID(), request.idempotencyKey(), requestFingerprint,
                request.triggerContext(), request.plannedWindowStart(), request.plannedWindowEnd(),
                universe.configurationVersion(), universe.fingerprintAlgorithm(), universe.fingerprint(),
                universe.resolvedAt(), diagnostics, now);
        if (resolutionFailed && noWork) {
            run = run.advanceTo(com.hope.trading.market_intelligence.domain.passive.PassiveScanRunStatus.FAILED, now);
        }
        repository.saveRun(run);
        if (noWork) return run;

        List<PassiveScanMarket> markets = new ArrayList<>();
        int ordinal = 0;
        for (PassiveMarketEntry entry : universe.entries()) {
            UUID childId = UUID.randomUUID();
            int attemptNumber = repository.findLatestMarketByMarketId(entry.marketId())
                    .filter(previous -> previous.status() == com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus.FAILED)
                    .map(previous -> previous.attemptCount() + 1)
                    .orElse(1);
            if (entry.skipReason() != null) {
                markets.add(PassiveScanMarket.skipped(childId, run.runId(), ordinal++, entry.marketId(),
                        entry.provider(), entry.productType(), entry.cadence(), entry.minimumEvidenceProfile(),
                        entry.skipReason(), now));
                continue;
            }
            String attemptKey = PassiveScanFingerprintFactory.attemptKey(run.runId(), entry.marketId(), attemptNumber);
            AnalysisExecution execution = executions.register(
                    new IntelligenceAnalysisRequest(UUID.randomUUID(), entry.marketId(), AnalysisExecutionMode.PASSIVE,
                            "PASSIVE_SCAN", null, null, null, null, EvidenceAcquisitionPolicy.LIVE_CURRENT,
                            null, null, entry.minimumEvidenceProfile()),
                    new IdempotencyKey(attemptKey), run.runId().toString(), run.runId().toString()
            );
            markets.add(PassiveScanMarket.registered(childId, run.runId(), ordinal++, entry.marketId(),
                    entry.provider(), entry.productType(), entry.cadence(), entry.minimumEvidenceProfile(),
                    execution.executionId(), attemptNumber, now));
        }
        repository.saveMarkets(markets);
        for (PassiveScanMarket market : markets) {
            if (market.analysisExecutionId() == null) continue;
            repository.saveAttempt(UUID.randomUUID(), market.scanMarketId(), market.attemptCount(), market.analysisExecutionId(),
                    PassiveScanFingerprintFactory.attemptKey(run.runId(), market.marketId(), market.attemptCount()), now);
        }
        return run;
    }
}
