package com.hope.trading.market_data.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OrderBookStorageHealthService {
    private final OrderBookStoragePressureGuard pressureGuard;
    private final OrderBookEvidenceCaptureService captureService;

    public OrderBookStorageHealthService(OrderBookStoragePressureGuard pressureGuard,
                                         OrderBookEvidenceCaptureService captureService) {
        this.pressureGuard = pressureGuard;
        this.captureService = captureService;
    }

    public StorageHealth health() {
        OrderBookStoragePressureGuard.StorageSnapshot storage = pressureGuard.snapshot();
        return new StorageHealth(storage.usableBytes(), storage.minimumFreeBytes(), storage.underPressure(),
                storage.error(), captureService.durabilityCompromised(), captureService.lastSuccessfulPersistence(),
                captureService.activeSequences(), captureService.durabilityCompromised() || storage.underPressure()
                ? "DEGRADED" : "HEALTHY");
    }

    public record StorageHealth(long usableBytes, long minimumFreeBytes, boolean underPressure,
                                String storageError, boolean durabilityCompromised,
                                Instant lastSuccessfulPersistence,
                                List<OrderBookEvidenceCaptureService.SequenceDiagnostics> activeSequences,
                                String captureState) {
        public StorageHealth {
            activeSequences = List.copyOf(activeSequences);
        }

        public boolean availableForCapture() {
            return !underPressure && !durabilityCompromised;
        }
    }
}
