package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import com.hope.trading.market_data.model.OrderBookDependencyState;
import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.model.OrderBookRetentionHold;
import com.hope.trading.market_data.model.OrderBookRetirementCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderBookRetentionService {
    private static final String ARTIFACT_SCHEMA_VERSION = "order-book-replay-artifact-v1";
    private final OrderBookRetentionRepository repository;
    private final OrderBookRetentionProperties properties;
    private final Clock clock;

    public OrderBookRetentionService(OrderBookRetentionRepository repository,
                                     OrderBookRetentionProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OrderBookReplayArtifact promote(OrderBookReplayResult replay, String semanticIdentity,
                                           String promotionReason) {
        if (replay == null || replay.status() != OrderBookReplayResult.ReplayStatus.AVAILABLE) {
            throw new IllegalArgumentException("only an available replay can be promoted");
        }
        if (semanticIdentity == null || semanticIdentity.isBlank()
                || promotionReason == null || promotionReason.isBlank()) {
            throw new IllegalArgumentException("semantic identity and promotion reason are required");
        }
        if (replay.marketId() == null || replay.sequenceId() == null || replay.evaluationCutoff() == null
                || replay.latestOccurredAt() == null || replay.latestKnownAt() == null
                || replay.sourceStartOrdinal() == null || replay.sourceEndOrdinal() == null
                || replay.sourceEvidenceFingerprint() == null || replay.sourceEvidenceFingerprint().isBlank()
                || replay.sourceTrustStatus() == null || replay.sourceTrustStatus().isBlank()) {
            throw new IllegalArgumentException("replay provenance is required");
        }
        if (!"TRUSTED".equals(replay.sourceTrustStatus())
                || replay.latestOccurredAt().isAfter(replay.evaluationCutoff())
                || replay.latestKnownAt().isAfter(replay.evaluationCutoff())
                || replay.latestOccurredAt().isAfter(replay.latestKnownAt())
                || replay.sourceStartOrdinal() < 0
                || replay.sourceEndOrdinal() < replay.sourceStartOrdinal()
                || replay.sourceEvidenceFingerprints() == null || replay.sourceEvidenceFingerprints().isEmpty()
                || replay.sourceEvidenceFingerprints().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("replay provenance is inconsistent or uses look-ahead data");
        }
        if (!repository.matchesPersistedEvidence(replay)) {
            throw new IllegalArgumentException("replay provenance does not match persisted evidence");
        }
        Instant createdAt = clock.instant();
        String fingerprint = fingerprint(replay, semanticIdentity, promotionReason);
        OrderBookReplayArtifact artifact = new OrderBookReplayArtifact(
                UUID.randomUUID(), semanticIdentity, fingerprint, replay.marketId(), replay.depth(),
                replay.sequenceId(), replay.evaluationCutoff(), replay.latestOccurredAt(), replay.latestKnownAt(),
                replay.sourceStartOrdinal(), replay.sourceEndOrdinal(), replay.provider(), replay.providerSymbol(),
                replay.normalizationVersion(), ARTIFACT_SCHEMA_VERSION, replay.sourceEvidenceFingerprint(),
                replay.sourceTrustStatus(), replay.sourceEvidenceFingerprints(), replay.bids(), replay.asks(),
                promotionReason, createdAt);
        if (repository.saveArtifact(artifact)) {
            return artifact;
        }
        OrderBookReplayArtifact existing = repository.findArtifact(semanticIdentity)
                .orElseThrow(() -> new IllegalStateException("promotion identity exists without an artifact"));
        if (!existing.artifactFingerprint().equals(artifact.artifactFingerprint())) {
            throw new IllegalStateException("promotion identity conflicts with an existing artifact");
        }
        return existing;
    }

    @Transactional
    public OrderBookEvidenceDependency protectDependency(String dependencyKey, String consumerType,
                                                         String consumerIdentity, UUID artifactId,
                                                         UUID sequenceId, String reason) {
        OrderBookEvidenceDependency dependency = new OrderBookEvidenceDependency(
                UUID.randomUUID(), dependencyKey, consumerType, consumerIdentity, artifactId, sequenceId,
                OrderBookDependencyState.PROTECTED, reason, clock.instant(), null);
        if (repository.saveDependency(dependency)) {
            return dependency;
        }
        OrderBookEvidenceDependency existing = repository.findDependency(dependencyKey)
                .orElseThrow(() -> new IllegalStateException("dependency identity exists without a dependency"));
        if (!sameTarget(existing, dependency)) {
            throw new IllegalStateException("dependency key conflicts with an existing target");
        }
        if (!java.util.Objects.equals(existing.consumerType(), dependency.consumerType())
                || !java.util.Objects.equals(existing.consumerIdentity(), dependency.consumerIdentity())
                || !java.util.Objects.equals(existing.reason(), dependency.reason())) {
            throw new IllegalStateException("dependency key conflicts with existing semantic identity");
        }
        return existing;
    }

    @Transactional
    public boolean releaseDependency(String dependencyKey, String reason) {
        if (repository.releaseDependency(dependencyKey, reason, clock.instant())) {
            return true;
        }
        return repository.findDependency(dependencyKey)
                .map(dependency -> dependency.state() == OrderBookDependencyState.RELEASED)
                .orElse(false);
    }

    @Transactional
    public OrderBookRetentionHold addHold(String holdKey, UUID artifactId, UUID sequenceId, String reason) {
        OrderBookRetentionHold hold = new OrderBookRetentionHold(
                UUID.randomUUID(), holdKey, artifactId, sequenceId, reason, clock.instant(), null);
        if (repository.saveHold(hold)) {
            return hold;
        }
        OrderBookRetentionHold existing = repository.findHold(holdKey)
                .orElseThrow(() -> new IllegalStateException("hold identity exists without a hold"));
        if (!sameTarget(existing, hold)) {
            throw new IllegalStateException("hold key conflicts with an existing target");
        }
        if (!java.util.Objects.equals(existing.reason(), hold.reason())) {
            throw new IllegalStateException("hold key conflicts with existing semantic identity");
        }
        return existing;
    }

    @Transactional
    public boolean releaseHold(String holdKey) {
        if (repository.releaseHold(holdKey, clock.instant())) {
            return true;
        }
        return repository.findHold(holdKey)
                .map(hold -> !hold.active())
                .orElse(false);
    }

    @Transactional
    public RetirementPreview previewRetirement() {
        Instant selectedAt = clock.instant();
        List<OrderBookRetirementCandidate> candidates = repository.findRetirementCandidates(
                selectedAt.minus(properties.getInvalidatedDiagnosticRetention()));
        String details = candidates.stream().map(repository::retirementDetails)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        String operationFingerprint = fingerprint(details);
        repository.recordDryRun(operationFingerprint, details, selectedAt, UUID.randomUUID());
        return new RetirementPreview(operationFingerprint, selectedAt, candidates);
    }

    @Transactional
    public int retire(String dryRunFingerprint, OrderBookRetirementCandidate candidate) {
        if (!properties.isDestructiveRetirementEnabled()) {
            throw new IllegalStateException("destructive Order Book retirement is disabled");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("retirement candidate is required");
        }
        if (dryRunFingerprint == null || dryRunFingerprint.isBlank()) {
            throw new IllegalArgumentException("dry-run fingerprint is required");
        }
        String details = repository.retirementDetails(candidate);
        String operationFingerprint = fingerprint(dryRunFingerprint + "|" + details);
        if (!repository.matchesDryRun(dryRunFingerprint, details)) {
            throw new IllegalStateException("retirement candidate is not confirmed by a recorded dry-run");
        }
        repository.recordMutation(operationFingerprint, details, clock.instant(), UUID.randomUUID());
        int retired = repository.retireEvidence(candidate, properties.getRetirementBatchSize(),
                clock.instant().minus(properties.getInvalidatedDiagnosticRetention()));
        if (retired < properties.getRetirementBatchSize()) {
            repository.completeOperation(operationFingerprint, clock.instant(), retired);
        } else {
            repository.recordPartialOperation(operationFingerprint, retired);
        }
        return retired;
    }

    private boolean sameTarget(OrderBookEvidenceDependency left, OrderBookEvidenceDependency right) {
        return java.util.Objects.equals(left.artifactId(), right.artifactId())
                && java.util.Objects.equals(left.sequenceId(), right.sequenceId());
    }

    private boolean sameTarget(OrderBookRetentionHold left, OrderBookRetentionHold right) {
        return java.util.Objects.equals(left.artifactId(), right.artifactId())
                && java.util.Objects.equals(left.sequenceId(), right.sequenceId());
    }

    private String fingerprint(OrderBookReplayResult replay, String semanticIdentity, String reason) {
        return fingerprint(String.join("|", semanticIdentity, reason, replay.marketId().toString(),
                Integer.toString(replay.depth()), replay.sequenceId().toString(), replay.evaluationCutoff().toString(),
                String.valueOf(replay.sourceStartOrdinal()), String.valueOf(replay.sourceEndOrdinal()),
                replay.provider(), replay.providerSymbol(), replay.normalizationVersion(),
                replay.sourceEvidenceFingerprint(), String.join(",", replay.sourceEvidenceFingerprints()),
                replay.sourceTrustStatus(), replay.latestOccurredAt().toString(), replay.latestKnownAt().toString(),
                replay.bids().toString(), replay.asks().toString()), replay.evaluationCutoff());
    }

    private String fingerprint(String material, Instant timestamp) {
        return fingerprint(material + "|" + timestamp);
    }

    private String fingerprint(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record RetirementPreview(String operationFingerprint, Instant selectedAt,
                                    List<OrderBookRetirementCandidate> candidates) {
        public RetirementPreview {
            candidates = List.copyOf(candidates);
        }
    }
}
