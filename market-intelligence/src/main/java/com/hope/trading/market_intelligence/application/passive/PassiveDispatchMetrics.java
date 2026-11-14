package com.hope.trading.market_intelligence.application.passive;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PassiveDispatchMetrics {
    private final MeterRegistry registry;
    private final Counter selected;
    private final Counter skipped;
    private final Counter dispatched;
    private final Counter retry;
    private final Counter duplicate;
    private final Counter insufficientEvidence;
    private final Counter providerBudget;
    private final Counter leaseRecovery;

    public PassiveDispatchMetrics(MeterRegistry registry) {
        this.registry = registry;
        selected = counter("selected");
        skipped = counter("skipped");
        dispatched = counter("dispatched");
        retry = counter("retry");
        duplicate = counter("duplicate");
        insufficientEvidence = counter("insufficient_evidence");
        providerBudget = counter("provider_budget");
        leaseRecovery = counter("lease_recovery");
    }

    public void selected(int count) { selected.increment(count); }
    public void skipped() { skipped.increment(); }
    public void dispatched() { dispatched.increment(); }
    public void retry() { retry.increment(); }
    public void duplicate() { duplicate.increment(); }
    public void insufficientEvidence() { insufficientEvidence.increment(); }
    public void providerBudget() { providerBudget.increment(); }
    public void leaseRecovery() { leaseRecovery.increment(); }

    private Counter counter(String outcome) {
        return registry.counter("trading_os.passive_dispatch", "outcome", outcome);
    }
}
