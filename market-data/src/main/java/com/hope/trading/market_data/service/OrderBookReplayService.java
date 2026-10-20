package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderBookReplayService {
    private final OrderBookEvidenceRepository repository;

    public OrderBookReplayService(OrderBookEvidenceRepository repository) {
        this.repository = repository;
    }

    public OrderBookReplayResult replay(UUID marketId, int depth, Instant cutoff) {
        if (marketId == null || depth < 1 || cutoff == null) {
            throw new IllegalArgumentException("marketId, depth and cutoff are required");
        }
        List<OrderBookEvidence> evidence = repository.findTrusted(marketId, depth, cutoff);
        Map<UUID, List<OrderBookEvidence>> bySequence = new HashMap<>();
        evidence.forEach(item -> bySequence.computeIfAbsent(item.sequenceId(), ignored -> new ArrayList<>()).add(item));

        OrderBookEvidence startingPoint = bySequence.values().stream()
                .flatMap(List::stream)
                .filter(item -> item.evidenceType() == OrderBookEvidenceType.SNAPSHOT)
                .max(Comparator.comparing(OrderBookEvidence::knownAt)
                        .thenComparingLong(OrderBookEvidence::sequenceOrdinal))
                .orElse(null);
        if (startingPoint == null) {
            return OrderBookReplayResult.unavailable(marketId, depth, cutoff,
                    "No trusted snapshot is known at the cutoff");
        }

        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        apply(bids, startingPoint.bids());
        apply(asks, startingPoint.asks());
        long expectedOrdinal = startingPoint.sequenceOrdinal();
        List<OrderBookEvidence> sequence = bySequence.get(startingPoint.sequenceId()).stream()
                .filter(item -> item.sequenceOrdinal() > startingPoint.sequenceOrdinal())
                .sorted(Comparator.comparingLong(OrderBookEvidence::sequenceOrdinal))
                .toList();
        for (OrderBookEvidence item : sequence) {
            if (item.evidenceType() != OrderBookEvidenceType.DELTA) {
                return OrderBookReplayResult.invalid(marketId, depth, cutoff, startingPoint.sequenceId(),
                        "Unexpected non-delta evidence after replay snapshot");
            }
            if (item.sequenceOrdinal() != expectedOrdinal + 1) {
                return OrderBookReplayResult.invalid(marketId, depth, cutoff, startingPoint.sequenceId(),
                        "Sequence is not contiguous at replay cutoff");
            }
            apply(bids, item.bids());
            apply(asks, item.asks());
            expectedOrdinal = item.sequenceOrdinal();
        }

        OrderBookEvidence lastEvidence = sequence.isEmpty() ? startingPoint : sequence.getLast();
        return new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, depth,
                startingPoint.sequenceId(), cutoff,
                lastEvidence.occurredAt(), lastEvidence.knownAt(),
                startingPoint.sequenceOrdinal(), lastEvidence.sequenceOrdinal(),
                startingPoint.provider().name(), startingPoint.providerSymbol(),
                startingPoint.normalizationVersion(), fingerprint(startingPoint, sequence),
                "TRUSTED",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(startingPoint), sequence.stream())
                        .map(OrderBookEvidence::contentFingerprint).toList(),
                limit(bids, depth), limit(asks, depth), null);
    }

    private String fingerprint(OrderBookEvidence startingPoint, List<OrderBookEvidence> sequence) {
        String material = java.util.stream.Stream.concat(java.util.stream.Stream.of(startingPoint), sequence.stream())
                .map(OrderBookEvidence::contentFingerprint)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private void apply(NavigableMap<BigDecimal, BigDecimal> book, List<OrderBookLevel> levels) {
        levels.forEach(level -> {
            if (level.quantity().signum() == 0) {
                book.remove(level.price());
            } else {
                book.put(level.price(), level.quantity());
            }
        });
    }

    private List<OrderBookLevel> limit(NavigableMap<BigDecimal, BigDecimal> book, int depth) {
        return book.entrySet().stream().limit(depth)
                .map(entry -> new OrderBookLevel(entry.getKey(), entry.getValue()))
                .toList();
    }
}
