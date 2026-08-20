package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.scan.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ActiveScanDispatchClaimService {
    private final ActiveScanRepository scans;
    private final AnalysisExecutionService executions;
    private final Clock clock;

    public ActiveScanDispatchClaimService(
            ActiveScanRepository scans,
            AnalysisExecutionService executions,
            Clock clock
    ) {
        this.scans = scans;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claimForDispatch(UUID scanId, UUID scanMarketId) {
        ActiveScanMarket market = scans.findMarketById(scanMarketId)
                .orElseThrow(() -> new ActiveScanException(
                        "ACTIVE_SCAN_MARKET_NOT_FOUND",
                        "Active scan market not found: " + scanMarketId,
                        404
                ));
        if (!market.scanId().equals(scanId)) {
            throw new ActiveScanException(
                    "ACTIVE_SCAN_MARKET_NOT_FOUND",
                    "Active scan market does not belong to scan: " + scanMarketId,
                    404
            );
        }
        if (!market.eligible() || market.analysisExecutionId() == null) {
            return ClaimResult.noDispatch(market.scanMarketId(), null);
        }
        if (market.status() == ActiveScanMarketStatus.DISPATCH_REQUESTED) {
            return resumeClaimed(scanId, market);
        }
        if (market.status() != ActiveScanMarketStatus.REGISTERED) {
            return ClaimResult.noDispatch(market.scanMarketId(), market.analysisExecutionId());
        }

        Instant now = clock.instant();
        boolean marketClaimed = scans.transitionMarketStatus(
                market.scanMarketId(),
                ActiveScanMarketStatus.REGISTERED,
                ActiveScanMarketStatus.DISPATCH_REQUESTED,
                now
        );
        if (!marketClaimed) {
            ActiveScanMarket reloaded = scans.findMarketById(scanMarketId)
                    .orElseThrow(() -> new ActiveScanException(
                            "ACTIVE_SCAN_MARKET_NOT_FOUND",
                            "Active scan market disappeared during dispatch claim: " + scanMarketId,
                            404
                    ));
            return reloaded.status() == ActiveScanMarketStatus.DISPATCH_REQUESTED
                    ? resumeClaimed(scanId, reloaded)
                    : ClaimResult.noDispatch(reloaded.scanMarketId(), reloaded.analysisExecutionId());
        }

        boolean childClaimed = executions.claimForDispatch(market.analysisExecutionId());
        AnalysisExecution execution = executions.find(market.analysisExecutionId());
        if (execution.status() == AnalysisExecutionStatus.ACCEPTED) {
            scans.transitionScanStatus(
                    scanId,
                    ActiveScanStatus.READY_TO_DISPATCH,
                    ActiveScanStatus.DISPATCH_REQUESTED,
                    now
            );
            return ClaimResult.dispatchNow(market.scanMarketId(), market.analysisExecutionId());
        }
        return ClaimResult.noDispatch(market.scanMarketId(), market.analysisExecutionId());
    }

    private ClaimResult resumeClaimed(UUID scanId, ActiveScanMarket market) {
        AnalysisExecution execution = executions.find(market.analysisExecutionId());
        if (execution.status() == AnalysisExecutionStatus.ACCEPTED) {
            scans.transitionScanStatus(
                    scanId,
                    ActiveScanStatus.READY_TO_DISPATCH,
                    ActiveScanStatus.DISPATCH_REQUESTED,
                    clock.instant()
            );
            return ClaimResult.dispatchNow(market.scanMarketId(), market.analysisExecutionId());
        }
        return ClaimResult.noDispatch(market.scanMarketId(), market.analysisExecutionId());
    }

    public record ClaimResult(
            UUID scanMarketId,
            UUID analysisExecutionId,
            boolean shouldDispatch
    ) {
        static ClaimResult dispatchNow(UUID scanMarketId, UUID analysisExecutionId) {
            return new ClaimResult(scanMarketId, analysisExecutionId, true);
        }

        static ClaimResult noDispatch(UUID scanMarketId, UUID analysisExecutionId) {
            return new ClaimResult(scanMarketId, analysisExecutionId, false);
        }
    }
}
