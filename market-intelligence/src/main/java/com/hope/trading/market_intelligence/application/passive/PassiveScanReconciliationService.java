package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.domain.passive.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PassiveScanReconciliationService {
    private final PassiveScanRunRepository repository;
    private final Clock clock;

    public PassiveScanReconciliationService(PassiveScanRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PassiveScanRun reconcile(java.util.UUID runId) {
        PassiveScanRun run = repository.findById(runId).orElseThrow();
        List<PassiveScanMarket> markets = repository.findMarketsByRunId(runId);
        if (markets.stream().anyMatch(value -> !value.status().isTerminal())) {
            if (markets.stream().anyMatch(value -> value.status() == PassiveScanMarketStatus.RUNNING
                    || value.status() == PassiveScanMarketStatus.ACCEPTED)) {
                repository.transitionRunStatus(runId, PassiveScanRunStatus.DISPATCH_REQUESTED,
                        PassiveScanRunStatus.RUNNING, "", clock.instant());
            }
            return repository.findById(runId).orElse(run);
        }
        PassiveScanRunStatus target = aggregate(run, markets);
        if (!run.status().isTerminal()) {
            repository.transitionRunStatus(runId, run.status(), target, summary(markets), clock.instant());
        }
        return repository.findById(runId).orElse(run);
    }

    private PassiveScanRunStatus aggregate(PassiveScanRun run, List<PassiveScanMarket> markets) {
        if (run.diagnostics().stream().anyMatch(value -> value.startsWith("CATALOGUE_UNAVAILABLE:"))) {
            return PassiveScanRunStatus.FAILED;
        }
        if (markets.isEmpty() || markets.stream().allMatch(value -> value.status() == PassiveScanMarketStatus.SKIPPED)) {
            return PassiveScanRunStatus.COMPLETED_NO_WORK;
        }
        long completed = markets.stream().filter(value -> value.status() == PassiveScanMarketStatus.COMPLETED).count();
        if (completed == markets.size()) return PassiveScanRunStatus.COMPLETED;
        if (completed > 0) return PassiveScanRunStatus.PARTIALLY_COMPLETED;
        return PassiveScanRunStatus.FAILED;
    }

    private String summary(List<PassiveScanMarket> markets) {
        long completed = markets.stream().filter(value -> value.status() == PassiveScanMarketStatus.COMPLETED).count();
        long insufficient = markets.stream().filter(value -> value.status() == PassiveScanMarketStatus.INSUFFICIENT_EVIDENCE).count();
        long failed = markets.stream().filter(value -> value.status() == PassiveScanMarketStatus.FAILED).count();
        long skipped = markets.stream().filter(value -> value.status() == PassiveScanMarketStatus.SKIPPED).count();
        return "total=" + markets.size() + ";completed=" + completed + ";failed=" + failed
                + ";insufficient=" + insufficient + ";skipped=" + skipped;
    }
}
