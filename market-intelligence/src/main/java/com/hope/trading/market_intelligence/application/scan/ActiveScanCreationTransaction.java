package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
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
public class ActiveScanCreationTransaction {
    private final ActiveScanRepository scans;
    private final ActiveScanScopeResolutionService scopeResolution;
    private final AnalysisExecutionService executions;
    private final ActiveScanChildKeyFactory childKeys;
    private final ActiveScanDispatchCoordinator dispatchCoordinator;
    private final Clock clock;
    private final PassiveCandidateScanValidator passiveCandidates;

    public ActiveScanCreationTransaction(
            ActiveScanRepository scans,
            ActiveScanScopeResolutionService scopeResolution,
            AnalysisExecutionService executions,
            ActiveScanChildKeyFactory childKeys,
            ActiveScanDispatchCoordinator dispatchCoordinator,
            Clock clock
    ) {
        this(scans, scopeResolution, executions, childKeys, dispatchCoordinator, clock, null);
    }

    /**
     * Production constructor. A {@code null} validator (legacy test wiring) makes any
     * passive-candidate request fail closed.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ActiveScanCreationTransaction(
            ActiveScanRepository scans,
            ActiveScanScopeResolutionService scopeResolution,
            AnalysisExecutionService executions,
            ActiveScanChildKeyFactory childKeys,
            ActiveScanDispatchCoordinator dispatchCoordinator,
            Clock clock,
            PassiveCandidateScanValidator passiveCandidates
    ) {
        this.passiveCandidates = passiveCandidates;
        this.scans = scans;
        this.scopeResolution = scopeResolution;
        this.executions = executions;
        this.childKeys = childKeys;
        this.dispatchCoordinator = dispatchCoordinator;
        this.clock = clock;
    }

    @Transactional
    public ActiveScan create(CreateActiveScanCommand command, String fingerprint) {
        ActiveScan existing = scans.findByActorIdAndIdempotencyKey(
                command.actorId(), command.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new ActiveScanException(
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another active scan request",
                        409
                );
            }
            return existing;
        }

        Instant createdAt = clock.instant();
        PassiveCandidateProvenance passiveCandidate = command.passiveCandidate()
                .map(reference -> validatePassiveCandidate(reference, command, createdAt))
                .orElse(null);
        ActiveScanScopeResolutionResult resolved = scopeResolution.resolve(
                new ActiveScanScopeResolutionRequest(
                        command.accountId(),
                        command.brokerAccountId(),
                        command.objective(),
                        command.requestedMarketIds(),
                        command.scopeMode()
                )
        );
        ActiveScanScopeSnapshot snapshot = ActiveScanScopeSnapshot.from(resolved);
        UUID scanId = UUID.randomUUID();
        ActiveScan scan = snapshot.effectiveMarketIds().isEmpty()
                ? ActiveScan.completedNoWork(
                scanId, command.actorId(), command.accountId(), resolved.objective(),
                command.idempotencyKey(), fingerprint, snapshot, createdAt, passiveCandidate
        )
                : ActiveScan.readyToDispatch(
                scanId, command.actorId(), command.accountId(), resolved.objective(),
                command.idempotencyKey(), fingerprint, snapshot, createdAt, passiveCandidate
        );
        scans.save(scan);
        scans.saveMarkets(buildMarkets(scan, resolved, resolved.assessmentCutoff(),
                command.tradeabilityEvidenceProfile()));
        registerAfterCommitIfNeeded(scan);
        return scan;
    }

    private PassiveCandidateProvenance validatePassiveCandidate(
            PassiveCandidateReference reference, CreateActiveScanCommand command, Instant now) {
        if (passiveCandidates == null) {
            throw new ActiveScanException("PASSIVE_CANDIDATE_VALIDATION_UNAVAILABLE",
                    "Passive candidate validation is unavailable", 503);
        }
        return passiveCandidates.validate(reference, command, now);
    }

    /** Idempotent replay never revalidates the passive candidate (Story 0243). */
    @Transactional
    public ActiveScan replay(ActiveScan existing) {
        registerAfterCommitIfNeeded(existing);
        return existing;
    }

    private List<ActiveScanMarket> buildMarkets(
            ActiveScan scan,
            ActiveScanScopeResolutionResult resolved,
            Instant now,
            com.hope.trading.market_intelligence.domain.TradeabilityEvidenceProfile profile
    ) {
        List<ActiveScanMarket> markets = new ArrayList<>();
        int ordinal = 0;
        for (MarketEligibilityDecision decision : resolved.decisions()) {
            UUID scanMarketId = UUID.randomUUID();
            if (!decision.eligible()) {
                ActiveScanMarket market = decision.status() == com.hope.trading.market_intelligence.domain.scope.MarketEligibilityStatus.NOT_EVALUABLE
                        ? ActiveScanMarket.notEvaluable(
                        scanMarketId, scan.scanId(), ordinal++, decision.marketId(), decision.reasons(), now)
                        : ActiveScanMarket.excluded(
                        scanMarketId, scan.scanId(), ordinal++, decision.marketId(), decision.reasons(), now);
                markets.add(market);
                continue;
            }
            AnalysisExecution execution = executions.register(
                    new IntelligenceAnalysisRequest(
                            UUID.randomUUID(),
                            decision.marketId(),
                            AnalysisExecutionMode.ACTIVE,
                            scan.objective(),
                            scan.accountId(),
                            scan.scanId(),
                            scanMarketId,
                            now,
                             EvidenceAcquisitionPolicy.LIVE_CURRENT
                    ).withTradeabilityEvidenceProfile(profile),
                    childKeys.forMarket(scan.scanId(), decision.marketId()),
                    scan.scanId().toString(),
                    scan.scanId().toString()
            );
            markets.add(ActiveScanMarket.registered(
                    scanMarketId, scan.scanId(), ordinal++, decision.marketId(),
                    execution.executionId(), now
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
}
