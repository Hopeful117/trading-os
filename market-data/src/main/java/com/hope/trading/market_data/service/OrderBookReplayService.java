package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookEvidence;
import com.hope.trading.market_data.model.OrderBookEvidenceOrigin;
import com.hope.trading.market_data.model.OrderBookEvidenceType;
import com.hope.trading.market_data.model.OrderBookLevel;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.config.OrderBookReplayProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Slf4j
public class OrderBookReplayService {
    private final OrderBookEvidenceRepository repository;
    private final OrderBookRetentionRepository retentionRepository;
    private final OrderBookReplayProperties properties;

    public OrderBookReplayService(OrderBookEvidenceRepository repository,
                                  OrderBookRetentionRepository retentionRepository) {
        this(repository, retentionRepository, new OrderBookReplayProperties());
    }

    @Autowired
    public OrderBookReplayService(OrderBookEvidenceRepository repository,
                                  OrderBookRetentionRepository retentionRepository,
                                  OrderBookReplayProperties properties) {
        this.repository = repository;
        this.retentionRepository = retentionRepository;
        this.properties = properties;
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
        OrderBookEvidence startingPoint = repository.findTrustedReplayBase(marketId, depth, cutoff).orElse(null);
        long repositoryDurationMs = elapsedMillis(repositoryStartedAt);
        if (startingPoint == null) {
            OrderBookReplayResult result = OrderBookReplayResult.unavailable(marketId, depth, cutoff,
                    "No trusted snapshot is known at the cutoff");
            log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                            + "repositoryMs={} assemblyMs={} totalMs={}",
                    marketId, depth, result.status(), 0, repositoryDurationMs,
                    elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
            return result;
        }

        ReplayAccumulator accumulator = new ReplayAccumulator(startingPoint, properties.getMaximumEvidenceCount());
        try {
            repository.streamTrustedEvidenceAfter(marketId, depth, startingPoint.sequenceId(),
                    startingPoint.sequenceOrdinal(), cutoff, properties.getBatchSize(), accumulator::accept);
        } catch (ReplayValidationException exception) {
            OrderBookReplayResult result = OrderBookReplayResult.invalid(marketId, depth,
                    cutoff, startingPoint.sequenceId(), exception.getMessage());
            log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                            + "repositoryMs={} assemblyMs={} totalMs={}",
                    marketId, depth, result.status(), accumulator.evidenceCount, repositoryDurationMs,
                    elapsedMillis(startedAt) - repositoryDurationMs, elapsedMillis(startedAt));
            return result;
        }

        OrderBookEvidence lastEvidence = accumulator.lastEvidence;
        OrderBookReplayResult result = new OrderBookReplayResult(
                OrderBookReplayResult.ReplayStatus.AVAILABLE, marketId, depth,
                startingPoint.sequenceId(), cutoff,
                lastEvidence.occurredAt(), lastEvidence.knownAt(),
                startingPoint.sequenceOrdinal(), lastEvidence.sequenceOrdinal(),
                startingPoint.provider().name(), startingPoint.providerSymbol(),
                startingPoint.normalizationVersion(), accumulator.fingerprint(),
                "TRUSTED",
                accumulator.fingerprints, limit(accumulator.bids, depth), limit(accumulator.asks, depth), null,
                accumulator.sourceOrigin);
        log.debug("Order-book replay completed marketId={} depth={} status={} evidenceCount={} "
                        + "repositoryMs={} assemblyMs={} totalMs={}",
                marketId, depth, result.status(), accumulator.evidenceCount, repositoryDurationMs,
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

    private final class ReplayAccumulator {
        private final NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        private final List<String> fingerprints = new ArrayList<>();
        private final MessageDigest digest = sha256();
        private final int maximumEvidenceCount;
        private OrderBookEvidence lastEvidence;
        private OrderBookEvidenceOrigin sourceOrigin;
        private long expectedOrdinal;
        private int evidenceCount;

        private ReplayAccumulator(OrderBookEvidence startingPoint, int maximumEvidenceCount) {
            this.maximumEvidenceCount = maximumEvidenceCount;
            if (maximumEvidenceCount < 1) {
                throw new IllegalArgumentException("maximumEvidenceCount must be positive");
            }
            apply(bids, startingPoint.bids());
            apply(asks, startingPoint.asks());
            addFingerprint(startingPoint.contentFingerprint());
            lastEvidence = startingPoint;
            expectedOrdinal = startingPoint.sequenceOrdinal();
            sourceOrigin = startingPoint.origin();
            evidenceCount = 1;
        }

        private void accept(OrderBookEvidence evidence) {
            if (evidence.evidenceType() != OrderBookEvidenceType.DELTA) {
                throw new ReplayValidationException("Unexpected non-delta evidence after replay snapshot");
            }
            if (evidence.sequenceOrdinal() != expectedOrdinal + 1) {
                throw new ReplayValidationException("Sequence is not contiguous at replay cutoff");
            }
            if (evidenceCount >= maximumEvidenceCount) {
                throw new ReplayValidationException("Replay evidence limit exceeded");
            }
            apply(bids, evidence.bids());
            apply(asks, evidence.asks());
            addFingerprint(evidence.contentFingerprint());
            sourceOrigin = sourceOrigin == evidence.origin() ? sourceOrigin : OrderBookEvidenceOrigin.MIXED;
            expectedOrdinal = evidence.sequenceOrdinal();
            lastEvidence = evidence;
            evidenceCount++;
        }

        private void addFingerprint(String fingerprint) {
            if (!fingerprints.isEmpty()) {
                digest.update((byte) '|');
            }
            digest.update(fingerprint.getBytes(StandardCharsets.UTF_8));
            fingerprints.add(fingerprint);
        }

        private String fingerprint() {
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest.digest()) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static final class ReplayValidationException extends RuntimeException {
        private ReplayValidationException(String message) {
            super(message);
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
