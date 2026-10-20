package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class OrderBookStoragePressureGuard {
    private final OrderBookRetentionProperties properties;

    public OrderBookStoragePressureGuard(OrderBookRetentionProperties properties) {
        this.properties = properties;
    }

    public StorageSnapshot snapshot() {
        try {
            FileStore store = Files.getFileStore(Path.of(properties.getStoragePath()));
            long usableBytes = store.getUsableSpace();
            return new StorageSnapshot(usableBytes, properties.getMinimumFreeBytes(),
                    usableBytes < properties.getMinimumFreeBytes(), null);
        } catch (IOException | RuntimeException exception) {
            return new StorageSnapshot(-1, properties.getMinimumFreeBytes(), true,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public boolean underPressure() {
        return snapshot().underPressure();
    }

    public record StorageSnapshot(long usableBytes, long minimumFreeBytes, boolean underPressure,
                                  String error) {
    }
}
