package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class OrderBookRetentionScheduler {
    private final OrderBookRetentionService retentionService;
    private final OrderBookRetentionProperties properties;
    private final ReentrantLock runLock = new ReentrantLock();

    @Scheduled(
            fixedDelayString = "${order-book.retention.scheduler-fixed-delay:PT1H}",
            initialDelayString = "${order-book.retention.scheduler-initial-delay:PT1M}")
    public void run() {
        if (!properties.isSchedulerEnabled() || !runLock.tryLock()) {
            return;
        }
        long startedAt = System.nanoTime();
        int candidateCount = 0;
        int retired = 0;
        int failures = 0;
        try {
            OrderBookRetentionService.RetirementPreview preview = retentionService.previewRetirement();
            candidateCount = preview.segments().size();
            if (properties.isDestructiveRetirementEnabled()) {
                for (var segment : preview.segments()) {
                    if (!segment.eligible()) {
                        continue;
                    }
                    try {
                        retired += retentionService.retire(preview.operationFingerprint(), segment);
                    } catch (RuntimeException exception) {
                        failures++;
                        log.error("Order-book retention segment failed candidateCount={} retired={} failures={}",
                                candidateCount, retired, failures, exception);
                    }
                }
            }
            log.info("Order-book retention run completed mode={} candidates={} retired={} failures={} durationMs={}",
                    properties.isDestructiveRetirementEnabled() ? "DESTRUCTIVE" : "DRY_RUN",
                    candidateCount, retired, failures, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error("Order-book retention run failed mode={} candidates={} retired={} failures={} durationMs={}",
                    properties.isDestructiveRetirementEnabled() ? "DESTRUCTIVE" : "DRY_RUN",
                    candidateCount, retired, failures + 1, elapsedMillis(startedAt), exception);
        } finally {
            runLock.unlock();
        }
    }

    private long elapsedMillis(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
