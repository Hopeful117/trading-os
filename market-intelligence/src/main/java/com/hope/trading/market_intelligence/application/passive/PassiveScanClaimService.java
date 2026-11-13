package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRunStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PassiveScanClaimService {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final PassiveScanRunRepository repository;
    private final AnalysisExecutionService executions;
    private final Clock clock;

    public PassiveScanClaimService(PassiveScanRunRepository repository,
                                   AnalysisExecutionService executions,
                                   Clock clock) {
        this.repository = repository;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID claim(UUID runId, UUID scanMarketId) {
        var market = repository.findMarketById(scanMarketId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown passive scan market: " + scanMarketId));
        if (!market.runId().equals(runId)) throw new IllegalArgumentException("Passive scan market is not owned by run");
        Instant now = clock.instant();
        UUID leaseId = UUID.randomUUID();
        if (!repository.claimMarket(scanMarketId, PassiveScanMarketStatus.REGISTERED, market.version(), leaseId,
                now.plus(LEASE), now)) return null;
        if (!executions.claimForDispatch(market.analysisExecutionId())) {
            throw new IllegalStateException("Passive analysis execution could not be claimed");
        }
        var claimed = repository.findMarketById(scanMarketId).orElseThrow();
        if (!repository.transitionMarketStatus(scanMarketId, PassiveScanMarketStatus.DISPATCH_REQUESTED,
                PassiveScanMarketStatus.ACCEPTED, claimed.version(), now)) {
            throw new IllegalStateException("Passive scan market claim was lost");
        }
        repository.transitionRunStatus(runId, PassiveScanRunStatus.REQUESTED,
                PassiveScanRunStatus.DISPATCH_REQUESTED, "", now);
        return leaseId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recover(UUID runId, UUID scanMarketId) {
        var market = repository.findMarketById(scanMarketId).orElseThrow();
        if (!market.runId().equals(runId) || market.leaseId() == null || market.leaseExpiresAt() == null) return false;
        Instant now = clock.instant();
        if (!market.leaseExpiresAt().isBefore(now)) return false;
        if (market.status() == PassiveScanMarketStatus.ACCEPTED) {
            executions.requeueAcceptedForRecovery(market.analysisExecutionId(), market.leaseExpiresAt(), now);
        }
        return repository.recoverExpiredMarket(scanMarketId, market.status(), market.leaseId(), market.version(), now);
    }
}
