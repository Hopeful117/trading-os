package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.port.PassiveDispatchCapacityRepository;
import com.hope.trading.market_intelligence.config.PassiveDispatchProperties;
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
    private final PassiveScanRunRepository repository;
    private final PassiveDispatchCapacityRepository capacity;
    private final AnalysisExecutionService executions;
    private final PassiveDispatchProperties properties;
    private final Clock clock;

    public PassiveScanClaimService(PassiveScanRunRepository repository,
                                   PassiveDispatchCapacityRepository capacity,
                                   AnalysisExecutionService executions,
                                   PassiveDispatchProperties properties,
                                   Clock clock) {
        this.repository = repository;
        this.capacity = capacity;
        this.executions = executions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID claim(UUID runId, UUID scanMarketId) {
        var market = repository.findMarketById(scanMarketId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown passive scan market: " + scanMarketId));
        if (!market.runId().equals(runId)) throw new IllegalArgumentException("Passive scan market is not owned by run");
        Instant now = clock.instant();
        UUID leaseId = UUID.randomUUID();
        Instant leaseExpiresAt = now.plus(properties.getClaimLease());
        if (!capacity.acquire(leaseId, scanMarketId, properties.getMaxConcurrentExecutions(), leaseExpiresAt, now)) {
            return null;
        }
        if (!repository.claimMarket(scanMarketId, PassiveScanMarketStatus.REGISTERED, market.version(), leaseId,
                leaseExpiresAt, now)) {
            capacity.release(leaseId, now);
            return null;
        }
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
        if (market.status() != PassiveScanMarketStatus.ACCEPTED
                && market.status() != PassiveScanMarketStatus.DISPATCH_REQUESTED) {
            return false;
        }
        if (market.status() == PassiveScanMarketStatus.ACCEPTED
                || market.status() == PassiveScanMarketStatus.DISPATCH_REQUESTED) {
            executions.requeueAcceptedForRecovery(market.analysisExecutionId(), market.leaseExpiresAt(), now);
        }
        boolean recovered = repository.recoverExpiredMarket(scanMarketId, market.status(), market.leaseId(), market.version(), now);
        if (recovered) capacity.release(market.leaseId(), now);
        return recovered;
    }

    public int recoverOrphanedCapacity() {
        return capacity.recoverExpired(clock.instant());
    }
}
