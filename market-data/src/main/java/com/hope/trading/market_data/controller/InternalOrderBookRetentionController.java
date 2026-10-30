package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookRetentionHold;
import com.hope.trading.market_data.model.OrderBookRetirementCandidate;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.service.OrderBookStorageHealthService;
import com.hope.trading.market_data.service.OrderBookEvidenceSegmentBackfillService;
import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.Instant;

@RestController
@RequestMapping("/internal/v1/order-book/retention")
@RequiredArgsConstructor
public class InternalOrderBookRetentionController {
    private final OrderBookRetentionService retentionService;
    private final OrderBookStorageHealthService storageHealthService;
    private final OrderBookEvidenceSegmentBackfillService backfillService;
    private final OrderBookRetentionProperties retentionProperties;

    @GetMapping("/health")
    public ResponseEntity<OrderBookStorageHealthService.StorageHealth> health() {
        return ResponseEntity.ok(storageHealthService.health());
    }

    @PostMapping("/backfill")
    public ResponseEntity<?> backfill(@RequestBody(required = false) BackfillRequest request) {
        BackfillRequest effectiveRequest = request == null ? new BackfillRequest(null, null) : request;
        try {
            int sequenceBatchSize = retentionProperties.resolveBackfillSequenceBatchSize(
                    effectiveRequest.sequenceBatchSize());
            int boundaryBatchSize = retentionProperties.resolveBackfillBoundaryBatchSize(
                    effectiveRequest.boundaryBatchSize());
            return ResponseEntity.ok(backfillService.backfill(sequenceBatchSize, boundaryBatchSize));
        } catch (OrderBookEvidenceSegmentBackfillService.BackfillInProgressException exception) {
            return ResponseEntity.status(409).body(new BackfillConflictResponse(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new BackfillRejectedResponse(exception.getMessage()));
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<OrderBookRetentionService.RetirementPreview> preview() {
        return ResponseEntity.ok(retentionService.previewRetirement());
    }

    @PostMapping("/retire")
    public ResponseEntity<Integer> retire(@RequestBody RetirementRequest request) {
        return ResponseEntity.ok(retentionService.retire(request.dryRunFingerprint(), request.candidate()));
    }

    @PostMapping("/artifacts")
    public ResponseEntity<OrderBookReplayArtifact> promote(@RequestBody PromotionRequest request) {
        return ResponseEntity.ok(retentionService.promote(request.replay(), request.semanticIdentity(),
                request.promotionReason()));
    }

    @PostMapping("/artifacts/canonical")
    public ResponseEntity<OrderBookReplayArtifact> promoteCanonical(@RequestBody CanonicalPromotionRequest request) {
        return ResponseEntity.ok(retentionService.promoteCanonical(request.marketId(), request.depth(),
                request.cutoff(), request.semanticIdentity(), request.promotionReason()));
    }

    @GetMapping("/artifacts/{semanticIdentity}/reusable")
    public ResponseEntity<Boolean> isReusableArtifact(@PathVariable String semanticIdentity) {
        return ResponseEntity.ok(retentionService.isReusableArtifact(semanticIdentity));
    }

    @PostMapping("/dependencies")
    public ResponseEntity<OrderBookEvidenceDependency> protectDependency(
            @RequestBody DependencyRequest request) {
        return ResponseEntity.ok(retentionService.protectDependency(request.dependencyKey(), request.consumerType(),
                request.consumerIdentity(), request.artifactId(), request.sequenceId(), request.segmentId(), request.reason()));
    }

    @DeleteMapping("/dependencies/{dependencyKey}")
    public ResponseEntity<Void> releaseDependency(@PathVariable String dependencyKey,
                                                  @RequestBody ReleaseRequest request) {
        retentionService.releaseDependency(dependencyKey, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/holds")
    public ResponseEntity<OrderBookRetentionHold> addHold(@RequestBody HoldRequest request) {
        return ResponseEntity.ok(retentionService.addHold(request.holdKey(), request.artifactId(),
                request.sequenceId(), request.segmentId(), request.reason()));
    }

    @DeleteMapping("/holds/{holdKey}")
    public ResponseEntity<Void> releaseHold(@PathVariable String holdKey) {
        retentionService.releaseHold(holdKey);
        return ResponseEntity.noContent().build();
    }

    public record DependencyRequest(String dependencyKey, String consumerType, String consumerIdentity,
                                    UUID artifactId, UUID sequenceId, UUID segmentId, String reason) {
        public DependencyRequest(String dependencyKey, String consumerType, String consumerIdentity,
                                 UUID artifactId, UUID sequenceId, String reason) {
            this(dependencyKey, consumerType, consumerIdentity, artifactId, sequenceId, null, reason);
        }
    }

    public record ReleaseRequest(String reason) {
    }

    public record HoldRequest(String holdKey, UUID artifactId, UUID sequenceId, UUID segmentId, String reason) {
        public HoldRequest(String holdKey, UUID artifactId, UUID sequenceId, String reason) {
            this(holdKey, artifactId, sequenceId, null, reason);
        }
    }

    public record RetirementRequest(String dryRunFingerprint, OrderBookRetirementCandidate candidate) {
    }

    public record BackfillRequest(Integer sequenceBatchSize, Integer boundaryBatchSize) {
    }

    public record BackfillConflictResponse(String message) {
    }

    public record BackfillRejectedResponse(String message) {
    }

    public record PromotionRequest(OrderBookReplayResult replay, String semanticIdentity,
                                   String promotionReason) {
    }

    public record CanonicalPromotionRequest(UUID marketId, int depth, Instant cutoff,
                                            String semanticIdentity, String promotionReason) {
    }
}
