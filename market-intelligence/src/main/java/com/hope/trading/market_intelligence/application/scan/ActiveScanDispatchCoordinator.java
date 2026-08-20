package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.ActiveScanRepository;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarket;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarketStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActiveScanDispatchCoordinator {
    private final ActiveScanRepository scans;
    private final ActiveScanDispatchClaimService claims;
    private final AnalysisExecutionService executions;

    public ActiveScanDispatchCoordinator(
            ActiveScanRepository scans,
            ActiveScanDispatchClaimService claims,
            AnalysisExecutionService executions
    ) {
        this.scans = scans;
        this.claims = claims;
        this.executions = executions;
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
