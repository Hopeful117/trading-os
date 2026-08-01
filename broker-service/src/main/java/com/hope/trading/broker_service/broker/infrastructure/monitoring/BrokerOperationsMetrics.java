package com.hope.trading.broker_service.broker.infrastructure.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

@Component
public final class BrokerOperationsMetrics {
    private static final Logger log=LoggerFactory.getLogger(BrokerOperationsMetrics.class);
    private final Map<String,LongAdder> requests=new ConcurrentHashMap<>(),failures=new ConcurrentHashMap<>(),latencyNanos=new ConcurrentHashMap<>();
    private final MeterRegistry meters;private final ObservationRegistry observations;
    public BrokerOperationsMetrics(MeterRegistry meters,ObservationRegistry observations){this.meters=meters;this.observations=observations;}
    public <T>T record(String operation,java.util.function.Supplier<T> action){
        return Observation.createNotStarted("broker.provider.operation",observations).lowCardinalityKeyValue("operation",operation).observe(()->timed(operation,action));
    }
    private <T>T timed(String operation,java.util.function.Supplier<T> action){long start=System.nanoTime();requests.computeIfAbsent(operation,k->new LongAdder()).increment();meters.counter("broker.provider.requests","operation",operation).increment();try{T result=action.get();meters.counter("broker.provider.results","operation",operation,"outcome","success").increment();log.info("broker_technical_audit operation={} result=SUCCESS",operation);return result;}catch(RuntimeException e){failures.computeIfAbsent(operation,k->new LongAdder()).increment();meters.counter("broker.provider.results","operation",operation,"outcome","failure","exception",e.getClass().getSimpleName()).increment();log.warn("broker_technical_audit operation={} result=FAILURE exception={}",operation,e.getClass().getSimpleName());throw e;}finally{long elapsed=System.nanoTime()-start;latencyNanos.computeIfAbsent(operation,k->new LongAdder()).add(elapsed);Timer.builder("broker.provider.duration").tag("operation",operation).register(meters).record(elapsed,java.util.concurrent.TimeUnit.NANOSECONDS);}}
    public void record(String operation,Runnable action){record(operation,()->{action.run();return null;});}
    public Map<String,Snapshot> snapshot(){Map<String,Snapshot> result=new java.util.TreeMap<>();requests.forEach((name,count)->result.put(name,new Snapshot(count.sum(),failures.getOrDefault(name,new LongAdder()).sum(),latencyNanos.getOrDefault(name,new LongAdder()).sum())));return Map.copyOf(result);}
    public record Snapshot(long requests,long failures,long totalLatencyNanos){}
}
