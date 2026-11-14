package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.PassiveDispatchCapacityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public class JpaPassiveDispatchCapacityRepository implements PassiveDispatchCapacityRepository {
    private final SpringDataPassiveDispatchCapacityStateRepository state;
    private final SpringDataPassiveDispatchCapacityLeaseRepository leases;
    private final SpringDataPassiveScanMarketRepository markets;

    public JpaPassiveDispatchCapacityRepository(SpringDataPassiveDispatchCapacityStateRepository state,
                                                 SpringDataPassiveDispatchCapacityLeaseRepository leases,
                                                 SpringDataPassiveScanMarketRepository markets) {
        this.state = state;
        this.leases = leases;
        this.markets = markets;
    }

    @Override
    @Transactional
    public boolean acquire(UUID leaseId, UUID scanMarketId, int capacity, Instant expiresAt, Instant now) {
        if (state.acquire(capacity, now) != 1) return false;
        JpaPassiveDispatchCapacityLeaseEntity lease = new JpaPassiveDispatchCapacityLeaseEntity();
        lease.leaseId = leaseId;
        lease.scanMarketId = scanMarketId;
        lease.expiresAt = expiresAt;
        lease.createdAt = now;
        leases.saveAndFlush(lease);
        return true;
    }

    @Override
    @Transactional
    public boolean release(UUID leaseId, Instant now) {
        if (leases.deleteLease(leaseId) != 1) return false;
        state.release(now);
        return true;
    }

    @Override
    @Transactional
    public int recoverExpired(Instant now) {
        int recovered = 0;
        for (JpaPassiveDispatchCapacityLeaseEntity lease : leases.findAll()) {
            var market = markets.findById(lease.scanMarketId);
            boolean safeToRelease = market.isEmpty()
                    || (market.get().leaseId == null
                    && ("REGISTERED".equals(market.get().status) || "SKIPPED".equals(market.get().status)))
                    || (market.get().leaseId != null && lease.leaseId.equals(market.get().leaseId)
                    && ("DISPATCH_REQUESTED".equals(market.get().status)
                    || "ACCEPTED".equals(market.get().status)));
            if (safeToRelease && lease.expiresAt.isBefore(now)
                    && leases.deleteExpiredLease(lease.leaseId, now) == 1) {
                state.release(now);
                recovered++;
            }
        }
        return recovered;
    }
}
