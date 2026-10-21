package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.OrderBookEvidenceDependency;
import com.hope.trading.market_data.model.OrderBookRetentionHold;
import com.hope.trading.market_data.model.OrderBookRetirementCandidate;
import com.hope.trading.market_data.model.OrderBookReplayArtifact;
import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.service.OrderBookStorageHealthService;
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

@RestController
@RequestMapping("/internal/v1/order-book/retention")
@RequiredArgsConstructor
public class InternalOrderBookRetentionController {
    private final OrderBookRetentionService retentionService;
    private final OrderBookStorageHealthService storageHealthService;

    @GetMapping("/health")
    public ResponseEntity<OrderBookStorageHealthService.StorageHealth> health() {
        return ResponseEntity.ok(storageHealthService.health());
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

    @PostMapping("/dependencies")
    public ResponseEntity<OrderBookEvidenceDependency> protectDependency(
            @RequestBody DependencyRequest request) {
        return ResponseEntity.ok(retentionService.protectDependency(request.dependencyKey(), request.consumerType(),
                request.consumerIdentity(), request.artifactId(), request.sequenceId(), request.reason()));
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
                request.sequenceId(), request.reason()));
    }

    @DeleteMapping("/holds/{holdKey}")
    public ResponseEntity<Void> releaseHold(@PathVariable String holdKey) {
        retentionService.releaseHold(holdKey);
        return ResponseEntity.noContent().build();
    }

    public record DependencyRequest(String dependencyKey, String consumerType, String consumerIdentity,
                                    UUID artifactId, UUID sequenceId, String reason) {
    }

    public record ReleaseRequest(String reason) {
    }

    public record HoldRequest(String holdKey, UUID artifactId, UUID sequenceId, String reason) {
    }

    public record RetirementRequest(String dryRunFingerprint, OrderBookRetirementCandidate candidate) {
    }

    public record PromotionRequest(OrderBookReplayResult replay, String semanticIdentity,
                                   String promotionReason) {
    }
}
