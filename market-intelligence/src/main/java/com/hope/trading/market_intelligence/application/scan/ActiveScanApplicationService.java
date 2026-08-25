package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionRequest;
import com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionResult;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ActiveScanApplicationService {
    private final ActiveScanRepository scans;
    private final ActiveScanScopeResolutionService scopeResolution;
    private final AnalysisExecutionService executions;
    private final ActiveScanFingerprintFactory fingerprints;
    private final ActiveScanChildKeyFactory childKeys;
    private final ActiveScanDispatchCoordinator dispatchCoordinator;
    private final ActiveScanReconciliationService reconciliation;
    private final Clock clock;

    public ActiveScanApplicationService(
            ActiveScanRepository scans,
            ActiveScanScopeResolutionService scopeResolution,
            AnalysisExecutionService executions,
            ActiveScanFingerprintFactory fingerprints,
            ActiveScanChildKeyFactory childKeys,
            ActiveScanDispatchCoordinator dispatchCoordinator,
            ActiveScanReconciliationService reconciliation,
            Clock clock
    ) {
        this.scans = scans;
        this.scopeResolution = scopeResolution;
        this.executions = executions;
        this.fingerprints = fingerprints;
        this.childKeys = childKeys;
        this.dispatchCoordinator = dispatchCoordinator;
        this.reconciliation = reconciliation;
        this.clock = clock;
    }

    @Transactional
    public ActiveScan create(CreateActiveScanCommand command) {
        String fingerprint = fingerprints.fingerprint(
                command.actorId(),
                command.accountId(),
                command.objective(),
                command.requestedMarketIds()
        );
        ActiveScan existing = scans.findByActorIdAndIdempotencyKey(
                command.actorId(),
                command.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new ActiveScanException(
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another active scan request",
                        409
                );
            }
            registerAfterCommitIfNeeded(existing);
            return existing;
        }

        Instant now = clock.instant();
        ActiveScanScopeResolutionResult resolved = scopeResolution.resolve(
                new ActiveScanScopeResolutionRequest(
                        command.accountId(),
                        command.objective(),
                        command.requestedMarketIds()
                )
        );
        ActiveScanScopeSnapshot snapshot = ActiveScanScopeSnapshot.from(resolved);
        UUID scanId = UUID.randomUUID();
        ActiveScan scan = snapshot.effectiveMarketIds().isEmpty()
                ? ActiveScan.completedNoWork(
                        scanId,
                        command.actorId(),
                        command.accountId(),
                        resolved.objective(),
                        command.idempotencyKey(),
                        fingerprint,
                        snapshot,
                        now
                )
                : ActiveScan.readyToDispatch(
                        scanId,
                        command.actorId(),
                        command.accountId(),
                        resolved.objective(),
                        command.idempotencyKey(),
                        fingerprint,
                        snapshot,
                        now
                );
        scans.save(scan);
        scans.saveMarkets(buildMarkets(scan, resolved, now));
        registerAfterCommitIfNeeded(scan);
        return scan;
    }

    @Transactional(readOnly = true)
    public ActiveScanView findOwned(UUID actorId, UUID scanId) {
        ActiveScan scan = scans.findByActorIdAndScanId(actorId, scanId)
                .orElseThrow(() -> new ActiveScanException(
                        "ACTIVE_SCAN_NOT_FOUND",
                        "Active scan not found: " + scanId,
                        404
                ));
        return new ActiveScanView(scan, scans.findMarketsByScanId(scanId));
    }

    public ActiveScanResultProjection findOwnedProjection(UUID actorId, UUID scanId) {
        return reconciliation.reconcileOwned(actorId, scanId);
    }

    private List<ActiveScanMarket> buildMarkets(
            ActiveScan scan,
            ActiveScanScopeResolutionResult resolved,
            Instant now
    ) {
        List<ActiveScanMarket> markets = new ArrayList<>();
        int ordinal = 0;
        for (MarketEligibilityDecision decision : resolved.decisions()) {
            UUID scanMarketId = UUID.randomUUID();
            if (!decision.eligible()) {
                markets.add(ActiveScanMarket.excluded(
                        scanMarketId,
                        scan.scanId(),
                        ordinal++,
                        decision.marketId(),
                        decision.reasons(),
                        now
                ));
                continue;
            }
            AnalysisExecution execution = executions.register(
                    new IntelligenceAnalysisRequest(
                            UUID.randomUUID(),
                            decision.marketId(),
                            AnalysisExecutionMode.ACTIVE,
                            scan.objective()
                    ),
                    childKeys.forMarket(scan.scanId(), decision.marketId()),
                    scan.scanId().toString(),
                    scan.scanId().toString()
            );
            markets.add(ActiveScanMarket.registered(
                    scanMarketId,
                    scan.scanId(),
                    ordinal++,
                    decision.marketId(),
                    execution.executionId(),
                    now
            ));
        }
        return markets;
    }

    private void registerAfterCommitIfNeeded(ActiveScan scan) {
        if (scan.status().isTerminal()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchCoordinator.resumeAsync(scan.scanId());
            }
        });
    }

    public record ActiveScanView(
            ActiveScan scan,
            List<ActiveScanMarket> markets
    ) {
        public ActiveScanView {
            markets = List.copyOf(markets);
        }
    }
}
