package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarket;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Owns the transition of persisted scan scope into analysis executions.
 *
 * {@code resume} walks the eligible children sequentially (claim + dispatch)
 * and must therefore never run on an HTTP request thread: callers go through
 * {@link #resumeAsync(UUID)}, which executes the loop on the dedicated
 * {@code scanDispatchExecutor} after the creating transaction has committed.
 */
@Component
public class ActiveScanDispatchCoordinator {
    private static final Logger log =
            LoggerFactory.getLogger(ActiveScanDispatchCoordinator.class);
    private final ActiveScanRepository scans;
    private final ActiveScanDispatchClaimService claims;
    private final AnalysisExecutionService executions;
    private final ExecutorService scanDispatchExecutor;

    public ActiveScanDispatchCoordinator(
            ActiveScanRepository scans,
            ActiveScanDispatchClaimService claims,
            AnalysisExecutionService executions,
            @Qualifier("scanDispatchExecutor") ExecutorService scanDispatchExecutor
    ) {
        this.scans = scans;
        this.claims = claims;
        this.executions = executions;
        this.scanDispatchExecutor = scanDispatchExecutor;
    }

    /**
     * Schedules {@link #resume(UUID)} off-thread. Never throws to the caller:
     * a worker failure is logged with the scanId. The scan then stays in its
     * pre-dispatch state; a client retry with the same Idempotency-Key
     * re-registers dispatch (idempotent replay path).
     */
    public void resumeAsync(UUID scanId) {
        scanDispatchExecutor.execute(() -> {
            try {
                log.info("Active scan dispatch worker started scanId={}", scanId);
                resume(scanId);
                log.info("Active scan dispatch worker completed scanId={}", scanId);
            } catch (RuntimeException exception) {
                log.error(
                        "Active scan dispatch worker failed scanId={}",
                        scanId,
                        exception
                );
            }
        });
    }

    public void resume(UUID scanId) {
        scans.findMarketsByScanId(scanId).stream()
                .filter(ActiveScanMarket::eligible)
                .filter(market -> market.status() == ActiveScanMarketStatus.REGISTERED
                        || market.status() == ActiveScanMarketStatus.DISPATCH_REQUESTED)
                .forEach(market -> {
                    ActiveScanDispatchClaimService.ClaimResult claim =
                            claims.claimForDispatch(scanId, market.scanMarketId());
                    if (claim.shouldDispatch()) {
                        executions.dispatchRegistered(claim.analysisExecutionId());
                    }
                });
    }
}
