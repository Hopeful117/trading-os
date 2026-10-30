package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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
@Slf4j
public class OrderBookReplayService {
    private final OrderBookEvidenceRepository repository;
    private final OrderBookRetentionRepository retentionRepository;

    public OrderBookReplayService(OrderBookEvidenceRepository repository,
                                  OrderBookRetentionRepository retentionRepository) {
        this.repository = repository;
        this.retentionRepository = retentionRepository;
    }

    // Artifact eligibility uses FOR SHARE so invalidation cannot commit mid-replay.
    @Transactional
    public OrderBookReplayResult replay(UUID marketId, int depth, Instant cutoff) {
        return replay(marketId, depth, cutoff, null);
    }

    @Transactional
    public OrderBookReplayResult replay(UUID marketId, int depth, Instant cutoff, String semanticIdentity) {
        if (marketId == null || depth < 1 || cutoff == null) {
            throw new IllegalArgumentException("marketId, depth and cutoff are required");
        }
        if (semanticIdentity != null && !semanticIdentity.isBlank()) {
            var artifactReplay = retentionRepository.findArtifact(semanticIdentity)
                    .filter(value -> matchesRequest(value, marketId, depth, cutoff))
                     .filter(value -> repository.isCurrentEligibleSequence(
                             value.sequenceId(), marketId, depth, cutoff))
                     .filter(retentionRepository::matchesArtifactFingerprint)
                     .map(this::fromArtifact);
            if (artifactReplay.isPresent()) return artifactReplay.get();
        }
        long startedAt = System.nanoTime();
        long repositoryStartedAt = System.nanoTime();
        List<OrderBookEvidence> evidence = repository.findTrusted(marketId, depth, cutoff);
        long repositoryDurationMs = elapsedMillis(repositoryStartedAt);
        Map<UUID, List<OrderBookEvidence>> bySequence = new HashMap<>();
        evidence.forEach(item -> bySequence.computeIfAbsent(item.sequenceId(), ignored -> new ArrayList<>()).add(item));

        OrderBookEvidence startingPoint = bySequence.values().stream()
                .flatMap(List::stream)
                .filter(item -> item.evidenceType() == OrderBookEvidenceType.SNAPSHOT)
                .max(Comparator.comparing(OrderBookEvidence::knownAt)
                        .thenComparingLong(OrderBookEvidence::sequenceOrdinal))
                .orElse(null);
        if (startingPoint == null) {
            OrderBookReplayResult result = OrderBookReplayResult.unavailable(marketId, depth, cutoff,
                    "No trusted snapshot is known at the cutoff");
            log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                            + "repositoryMs={} assemblyMs={} totalMs={}",
                    marketId, depth, result.status(), evidence.size(), repositoryDurationMs,
                    elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
            return result;
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
                OrderBookReplayResult result = OrderBookReplayResult.invalid(marketId, depth, cutoff,
                        startingPoint.sequenceId(),
                        "Unexpected non-delta evidence after replay snapshot");
                log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                                + "repositoryMs={} assemblyMs={} totalMs={}",
                        marketId, depth, result.status(), evidence.size(), repositoryDurationMs,
                        elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
                return result;
            }
            if (item.sequenceOrdinal() != expectedOrdinal + 1) {
                OrderBookReplayResult result = OrderBookReplayResult.invalid(marketId, depth, cutoff,
                        startingPoint.sequenceId(),
                        "Sequence is not contiguous at replay cutoff");
                log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                                + "repositoryMs={} assemblyMs={} totalMs={}",
                        marketId, depth, result.status(), evidence.size(), repositoryDurationMs,
                        elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
                return result;
            }
            apply(bids, item.bids());
            apply(asks, item.asks());
            expectedOrdinal = item.sequenceOrdinal();
        }

        OrderBookEvidence lastEvidence = sequence.isEmpty() ? startingPoint : sequence.getLast();
        OrderBookEvidenceOrigin sourceOrigin = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(startingPoint), sequence.stream())
                .map(OrderBookEvidence::origin)
                .distinct()
                .reduce((left, right) -> OrderBookEvidenceOrigin.MIXED)
                .orElse(startingPoint.origin());
        OrderBookReplayResult result = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, depth,
                startingPoint.sequenceId(), cutoff,
                lastEvidence.occurredAt(), lastEvidence.knownAt(),
                startingPoint.sequenceOrdinal(), lastEvidence.sequenceOrdinal(),
                startingPoint.provider().name(), startingPoint.providerSymbol(),
                startingPoint.normalizationVersion(), fingerprint(startingPoint, sequence),
                "TRUSTED",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(startingPoint), sequence.stream())
                        .map(OrderBookEvidence::contentFingerprint).toList(),
                limit(bids, depth), limit(asks, depth), null, sourceOrigin);
        log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                        + "repositoryMs={} assemblyMs={} totalMs={}",
                marketId, depth, result.status(), evidence.size(), repositoryDurationMs,
                elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
        return result;
    }

    private boolean matchesRequest(OrderBookReplayArtifact artifact, UUID marketId, int depth, Instant cutoff) {
        return "TRUSTED".equals(artifact.sourceTrustStatus())
                && artifact.marketId().equals(marketId)
                && artifact.depth() == depth
                && artifact.evaluationCutoff().equals(cutoff)
                 && (artifact.artifactSchemaVersion().equals("order-book-replay-artifact-v2")
                 || artifact.artifactSchemaVersion().equals("order-book-replay-artifact-v3"))
                && artifact.normalizationVersion().equals(OrderBookEvidenceFingerprint.NORMALIZATION_VERSION)
                && artifact.latestOccurredAt() != null
                && artifact.latestKnownAt() != null
                && !artifact.latestOccurredAt().isAfter(cutoff)
                && !artifact.latestKnownAt().isAfter(cutoff)
                && artifact.sourceStartOrdinal() != null
                && artifact.sourceEndOrdinal() != null
                && artifact.sourceStartOrdinal() >= 0
                && artifact.sourceEndOrdinal() >= artifact.sourceStartOrdinal();
    }

    private OrderBookReplayResult fromArtifact(OrderBookReplayArtifact artifact) {
        return new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, artifact.marketId(), artifact.depth(),
                artifact.sequenceId(), artifact.evaluationCutoff(), artifact.latestOccurredAt(),
                artifact.latestKnownAt(), artifact.sourceStartOrdinal(), artifact.sourceEndOrdinal(),
                artifact.provider(), artifact.providerSymbol(), artifact.normalizationVersion(),
                artifact.sourceEvidenceFingerprint(), artifact.sourceTrustStatus(),
                artifact.sourceEvidenceFingerprints(), artifact.bids(), artifact.asks(), null,
                artifact.sourceEvidenceOrigin());
    }

    private long elapsedMillis(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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
