package com.hope.trading.market_intelligence.adapter.observability;

import com.hope.trading.market_intelligence.application.port.TradePlanningMetrics;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

public final class InMemoryTradePlanningMetrics implements TradePlanningMetrics {
    private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final LongAdder totalPlanningNanos = new LongAdder();
    @Override public void increment(String metric) {
        counters.computeIfAbsent(metric, ignored -> new LongAdder()).increment();
    }
    @Override public void recordDuration(Duration duration) {
        totalPlanningNanos.add(duration.toNanos());
    }
    public long count(String metric) {
        return counters.getOrDefault(metric, new LongAdder()).sum();
    }
    public long totalPlanningNanos() { return totalPlanningNanos.sum(); }
    @Override public java.util.Map<String, Long> snapshot() {
        java.util.Map<String, Long> values = new java.util.TreeMap<>();
        counters.forEach((name, count) -> values.put(name, count.sum()));
        values.put("trade_planning_total_nanos", totalPlanningNanos());
        return java.util.Map.copyOf(values);
    }
}
