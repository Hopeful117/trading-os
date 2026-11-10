package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalEvidenceRetirementBusinessState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementOperationalState;
import com.hope.trading.market_data.model.HistoricalEvidenceRetirementScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderBookSourceRetirementService {
    private final OrderBookSourceRetirementRepository repository;
    private final Clock clock;

    public OrderBookSourceRetirementService(OrderBookSourceRetirementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public OperationView prepare(PrepareRequest request) {
        if (request == null || request.scope() == null || blank(request.approvalReference())
                || blank(request.actor())) {
            throw new IllegalArgumentException("scope, approval reference and actor are required");
        }
        Instant now = clock.instant();
        if (!repository.approvalValid(request.scope(), request.approvalReference(), request.actor(), now)) {
            throw new IllegalStateException("source-retirement approval is missing, expired, revoked or mismatched");
        }
        OrderBookSourceRetirementRepository.Preflight preflight = repository.preflight(request.scope());
        if (!preflight.passed()) {
            throw new IllegalStateException("source-retirement preflight failed: targetRows="
                    + preflight.targetRows() + ", ambiguousSequenceCount=" + preflight.ambiguousSequenceCount()
                    + ", holdCount=" + preflight.holdCount() + ", catalogSafe=" + preflight.catalogSafe());
        }
        String operationFingerprint = fingerprint(request.scope() + "|" + request.approvalReference()
                + "|" + preflight.fingerprint());
        UUID operationId = UUID.nameUUIDFromBytes(("order-book-source-retirement:" + operationFingerprint)
                .getBytes(StandardCharsets.UTF_8));
        OrderBookSourceRetirementRepository.Operation operation = new OrderBookSourceRetirementRepository.Operation(
                operationId, operationFingerprint, request.scope(), request.approvalReference(), request.actor(),
                HistoricalEvidenceRetirementBusinessState.PREPARED,
                HistoricalEvidenceRetirementOperationalState.PREFLIGHT_PASSED,
                preflight.fingerprint(), preflight.inventoryFingerprint(), null, null,
                now, now);
        if (!repository.savePrepared(operation)) {
            return repository.find(operationId).map(OperationView::from).orElseThrow(
                    () -> new IllegalStateException("operation identity exists without durable operation"));
        }
        return OperationView.from(operation);
    }

    @Transactional
    public OperationView sourceRetired(UUID operationId, String dependencyFingerprint, String actor, String reason) {
        if (operationId == null || blank(dependencyFingerprint) || blank(actor) || blank(reason)) {
            throw new IllegalArgumentException("operation, dependency fingerprint, actor and reason are required");
        }
        OrderBookSourceRetirementRepository.Operation current = repository.findForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("unknown retirement operation"));
        if (!dependencyFingerprint.equals(current.inventoryFingerprint())) {
            throw new IllegalStateException("source-retirement acknowledgement does not match prepared inventory");
        }
        if (current.businessState() == HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED) {
            if (!dependencyFingerprint.equals(current.dependencyFingerprint())
                    || !repository.hasAudit(operationId, "SOURCE_RETIRED", "PREFLIGHT_PASSED", actor, reason, null)) {
                throw new IllegalStateException("source-retirement acknowledgement conflicts with existing fingerprint");
            }
            return OperationView.from(current);
        }
        if (current.businessState() != HistoricalEvidenceRetirementBusinessState.PREPARED
                || current.operationalState() != HistoricalEvidenceRetirementOperationalState.PREFLIGHT_PASSED) {
            throw new IllegalStateException("source retirement requires a prepared preflight operation");
        }
        OrderBookSourceRetirementRepository.Preflight currentPreflight = repository.preflight(current.scope());
        if (!currentPreflight.passed()
                || !current.preflightFingerprint().equals(currentPreflight.fingerprint())
                || !current.inventoryFingerprint().equals(currentPreflight.inventoryFingerprint())) {
            throw new IllegalStateException("source-retirement inventory is stale or no longer eligible");
        }
        if (!dependencyFingerprint.equals(currentPreflight.inventoryFingerprint())) {
            throw new IllegalStateException("source-retirement acknowledgement does not match current inventory");
        }
        boolean transitioned = repository.transitionSourceRetired(operationId, dependencyFingerprint, actor, reason,
                clock.instant(), UUID.randomUUID(), fingerprint(operationId + "|SOURCE_RETIRED|"
                        + dependencyFingerprint + "|" + actor + "|" + reason));
        if (!transitioned) throw new IllegalStateException("source-retirement transition was not accepted");
        return repository.find(operationId).map(OperationView::from).orElseThrow();
    }

    @Transactional(readOnly = true)
    public OperationView get(UUID operationId) {
        return repository.find(operationId).map(OperationView::from)
                .orElseThrow(() -> new IllegalArgumentException("unknown retirement operation"));
    }

    @Transactional(readOnly = true)
    public boolean isSourceRetired(UUID marketId, int depth) {
        return repository.isSourceRetired(marketId, depth);
    }

    @Transactional
    public OperationView transitionOperational(UUID operationId,
                                               HistoricalEvidenceRetirementOperationalState next,
                                               String actor, String reason, String externalReference) {
        if (operationId == null || next == null || blank(actor) || blank(reason)) {
            throw new IllegalArgumentException("operation, state, actor and reason are required");
        }
        if ((next == HistoricalEvidenceRetirementOperationalState.BACKUP_VALIDATED
                || next == HistoricalEvidenceRetirementOperationalState.QUIESCENCE_CONFIRMED
                || next == HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS
                || next == HistoricalEvidenceRetirementOperationalState.PHYSICAL_RETIRED)
                && blank(externalReference)) {
            throw new IllegalArgumentException("an external evidence reference is required for this transition");
        }
        OrderBookSourceRetirementRepository.Operation current = repository.findForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("unknown retirement operation"));
        if (requiresSourceRetired(next)
                && current.businessState() != HistoricalEvidenceRetirementBusinessState.SOURCE_RETIRED) {
            throw new IllegalStateException("physical retirement requires SOURCE_RETIRED business state");
        }
        if (current.operationalState() == next) {
            if (!repository.hasAudit(operationId, current.businessState().name(), next.name(), actor, reason,
                    externalReference)) {
                throw new IllegalStateException("operational transition conflicts with existing audit");
            }
            return OperationView.from(current);
        }
        if (!allowed(current.operationalState(), next)) {
            throw new IllegalStateException("invalid source-retirement operational transition");
        }
        boolean transitioned = repository.transitionOperational(operationId, current.operationalState(), next, actor,
                reason, externalReference, clock.instant(), UUID.randomUUID(),
                fingerprint(operationId + "|" + current.operationalState() + "|" + next + "|" + reason));
        if (!transitioned) throw new IllegalStateException("operational transition was not accepted");
        return get(operationId);
    }

    private boolean allowed(HistoricalEvidenceRetirementOperationalState current,
                            HistoricalEvidenceRetirementOperationalState next) {
        return switch (current) {
            case NOT_STARTED -> next == HistoricalEvidenceRetirementOperationalState.PREFLIGHT_PASSED
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED;
            case PREFLIGHT_PASSED -> next == HistoricalEvidenceRetirementOperationalState.BACKUP_VALIDATED
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED
                    || next == HistoricalEvidenceRetirementOperationalState.AMBIGUOUS;
            case BACKUP_VALIDATED -> next == HistoricalEvidenceRetirementOperationalState.QUIESCENCE_CONFIRMED
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED
                    || next == HistoricalEvidenceRetirementOperationalState.AMBIGUOUS;
            case QUIESCENCE_CONFIRMED -> next == HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED
                    || next == HistoricalEvidenceRetirementOperationalState.AMBIGUOUS;
            case PHYSICAL_IN_PROGRESS -> next == HistoricalEvidenceRetirementOperationalState.PHYSICAL_RETIRED
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED
                    || next == HistoricalEvidenceRetirementOperationalState.AMBIGUOUS;
            case FAILED, AMBIGUOUS -> next == HistoricalEvidenceRetirementOperationalState.RECONCILIATION_REQUIRED;
            case RECONCILIATION_REQUIRED -> next == HistoricalEvidenceRetirementOperationalState.PREFLIGHT_PASSED
                    || next == HistoricalEvidenceRetirementOperationalState.FAILED;
            case PHYSICAL_RETIRED -> false;
        };
    }

    private boolean requiresSourceRetired(HistoricalEvidenceRetirementOperationalState state) {
        return state == HistoricalEvidenceRetirementOperationalState.BACKUP_VALIDATED
                || state == HistoricalEvidenceRetirementOperationalState.QUIESCENCE_CONFIRMED
                || state == HistoricalEvidenceRetirementOperationalState.PHYSICAL_IN_PROGRESS
                || state == HistoricalEvidenceRetirementOperationalState.PHYSICAL_RETIRED;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String fingerprint(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record PrepareRequest(HistoricalEvidenceRetirementScope scope, String approvalReference, String actor) {
    }

    public record OperationView(UUID operationId, String operationFingerprint,
                                HistoricalEvidenceRetirementScope scope, String approvalReference, String actor,
                                HistoricalEvidenceRetirementBusinessState businessState,
                                HistoricalEvidenceRetirementOperationalState operationalState,
                                String preflightFingerprint, String inventoryFingerprint,
                                String dependencyFingerprint, String failureReason, Instant createdAt,
                                Instant updatedAt) {
        private static OperationView from(OrderBookSourceRetirementRepository.Operation operation) {
            return new OperationView(operation.operationId(), operation.operationFingerprint(), operation.scope(),
                    operation.approvalReference(), operation.actor(), operation.businessState(),
                    operation.operationalState(), operation.preflightFingerprint(), operation.inventoryFingerprint(),
                    operation.dependencyFingerprint(), operation.failureReason(), operation.createdAt(),
                    operation.updatedAt());
        }
    }
}
