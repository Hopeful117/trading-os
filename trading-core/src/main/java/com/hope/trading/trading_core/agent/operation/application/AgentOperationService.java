package com.hope.trading.trading_core.agent.operation.application;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.*;
import com.hope.trading.trading_core.agent.operation.infrastructure.*;
import com.hope.trading.trading_core.agent.operation.model.*;
import com.hope.trading.trading_core.agent.operation.repository.AgentOperationRequestRepository;
import com.hope.trading.trading_core.agent.operation.api.AgentOperationDtos;
import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountSnapshotIncompleteException;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AgentOperationService {
    private final AgentOperationRequestRepository requests;
    private final AgentOperationAuditEventRepository audit;
    private final AgentAuthorizationService authorization;
    private final BrokerAccountRepository accounts;
    private final BrokerSynchronizationService synchronization;
    private final AgentOperationClaimService claims;
    private final Clock clock;
    @Value("${trading-core.agent-operation.approval-ttl-seconds:300}") private long approvalTtlSeconds;

    @Transactional
    public AgentOperationDtos.Response request(AgentPrincipal agent, AgentOperationDtos.CreateRequest input, String correlationId) {
        if (input == null) {
            throw new AgentOperationException(400, "INVALID_OPERATION_REQUEST");
        }
        var decision = requireScope(agent, input.operationType(), input.resourceType(), input.resourceId(), correlationId);
        Instant now = clock.instant();
        var request = AgentOperationRequestEntity.create(agent.agentId(), agent.ownerUserId(), input.operationType(),
                input.resourceId(), now, now.plusSeconds(Math.max(30, approvalTtlSeconds)), correlationId,
                decision.grantId(), decision.grantVersion(), decision.agentVersion());
        try {
            requests.saveAndFlush(request); safeEvent(request, "REQUESTED", "PENDING_APPROVAL", correlationId, "AGENT", agent.agentId(), null, null, now);
            return AgentOperationDtos.Response.of(request);
        } catch (DataIntegrityViolationException duplicate) {
            throw new AgentOperationException(409, "ACTIVE_REQUEST_EXISTS");
        }
    }

    @Transactional
    public List<AgentOperationDtos.Response> listForHuman(UUID ownerId) {
        return requests.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerId).stream().map(this::expireIfNeeded).map(AgentOperationDtos.Response::of).toList();
    }

    @Transactional
    public AgentOperationDtos.Response humanGet(UUID ownerId, UUID id) {
        return AgentOperationDtos.Response.of(expireIfNeeded(requests.findByIdAndOwnerUserId(id, ownerId).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND"))));
    }

    @Transactional
    public AgentOperationDtos.Response approve(UUID ownerId, UUID id, String correlationId) {
        var request = owned(id, ownerId); validatePending(request);
        requireScopeForRequest(request, correlationId);
        Instant now = clock.instant();
        request.approve(ownerId, now); requests.save(request); safeEvent(request, "APPROVED", "APPROVED", correlationId, "HUMAN", ownerId, null, null, now);
        return AgentOperationDtos.Response.of(request);
    }

    @Transactional
    public AgentOperationDtos.Response reject(UUID ownerId, UUID id, String correlationId) {
        var request = owned(id, ownerId); validatePending(request);
        requireScopeForRequest(request, correlationId);
        Instant now = clock.instant();
        request.reject(ownerId, now);
        requests.save(request); safeEvent(request, "REJECTED", "REJECTED", correlationId, "HUMAN", ownerId, null, null, now); return AgentOperationDtos.Response.of(request);
    }

    @Transactional(readOnly = true)
    public AgentOperationDtos.Response agentGet(AgentPrincipal agent, UUID id, String correlationId) {
        var request = requests.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND"));
        if (!agent.ownerUserId().equals(request.ownerUserId())) throw new AgentOperationException(403, "OWNER_MISMATCH");
        requireScopeForRequest(request, correlationId);
        return AgentOperationDtos.Response.of(request);
    }

    public AgentOperationDtos.Response execute(AgentPrincipal agent, UUID id, String correlationId) {
        var request = requests.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND"));
        if (!agent.ownerUserId().equals(request.ownerUserId())) throw new AgentOperationException(403, "OWNER_MISMATCH");
        requireScopeForRequest(request, correlationId);
        if (!claims.claim(id, agent.agentId(), clock.instant())) {
            return AgentOperationDtos.Response.of(requests.findById(id).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND")));
        }
        request = requests.findById(id).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND"));
        safeEvent(request, "EXECUTION_CLAIMED", "EXECUTING", correlationId, "AGENT", agent.agentId(), null, null, clock.instant());
        UUID invocationId = UUID.randomUUID();
        try {
            safeEvent(request, "BROKER_INVOCATION", "DISPATCHED", correlationId, "AGENT", agent.agentId(), invocationId, null, clock.instant());
            synchronization.synchronize(agent.ownerUserId(), request.resourceId());
            complete(id, true, null);
        } catch (RuntimeException failure) {
            complete(id, false, classify(failure));
        }
        return AgentOperationDtos.Response.of(requests.findById(id).orElseThrow());
    }

    @Transactional
    void complete(UUID id, boolean success, String failureClassification) {
        var request = requests.findById(id).orElseThrow(); Instant now = clock.instant();
        if (request.status() == AgentOperationStatus.EXECUTING) {
            if (success) request.succeeded(now); else if ("DETERMINISTIC".equals(failureClassification)) {
                request.failed(now, failureClassification);
            } else request.unknown(now, failureClassification);
            requests.save(request);
            safeEvent(request, success ? "COMPLETED" : request.outcome(), request.outcome(), request.correlationId(),
                    "SYSTEM", null, null, failureClassification, now);
        }
    }

    private AgentOperationRequestEntity expireIfNeeded(AgentOperationRequestEntity request) {
        if (request.expiresAt().isAfter(clock.instant()) || request.status() == AgentOperationStatus.SUCCEEDED
                || request.status() == AgentOperationStatus.FAILED || request.status() == AgentOperationStatus.REJECTED
                || request.status() == AgentOperationStatus.EXPIRED || request.status() == AgentOperationStatus.OUTCOME_UNKNOWN
                || request.status() == AgentOperationStatus.EXECUTING) return request;
        request.expire(); requests.save(request); safeEvent(request, "EXPIRED", "EXPIRED", request.correlationId(), "SYSTEM", null, null, null, clock.instant()); return request;
    }
    private AgentOperationRequestEntity owned(UUID id, UUID ownerId) { return requests.findByIdAndOwnerUserId(id, ownerId).orElseThrow(() -> new AgentOperationException(404, "NOT_FOUND")); }
    private void validatePending(AgentOperationRequestEntity r) { if (r.status() != AgentOperationStatus.PENDING_APPROVAL || !r.expiresAt().isAfter(clock.instant())) { if (r.status() == AgentOperationStatus.PENDING_APPROVAL) r.expire(); throw new AgentOperationException(409, "REQUEST_NOT_PENDING"); } }
    private void requireScopeForRequest(AgentOperationRequestEntity r, String correlationId) { requireScope(new AgentPrincipal(r.agentId(), r.ownerUserId(), null, 0, null), r.operationType(), r.resourceType(), r.resourceId(), correlationId); }
    private AgentAuthorizationService.Decision requireScope(AgentPrincipal agent, AgentOperationType type, String resourceType, UUID resourceId, String correlationId) {
        if (type != AgentOperationType.ACCOUNT_SYNCHRONIZATION || !"BROKER_ACCOUNT".equals(resourceType)
                || resourceId == null || !accounts.findByIdAndOwnerId(resourceId, agent.ownerUserId()).isPresent()
                ) {
            throw new AgentOperationException(403, "OPERATION_NOT_AUTHORIZED");
        }
        var decision = authorization.authorize(agent.agentId(), AgentPermission.ACCOUNTS_SYNC,
                AgentResourceType.BROKER_ACCOUNT, resourceId, correlationId);
        if (!decision.allowed()) throw new AgentOperationException(403, "OPERATION_NOT_AUTHORIZED");
        return decision;
    }
    @Transactional
    public int recoverStaleExecuting(Duration age) {
        int recovered = 0;
        Instant cutoff = clock.instant().minus(age);
        for (var request : requests.findStaleExecuting(cutoff)) {
            request.unknown(clock.instant(), "RECOVERY_REQUIRED");
            requests.save(request);
            safeEvent(request, "OUTCOME_UNKNOWN", request.outcome(), request.correlationId(), "SYSTEM", null, null, "RECOVERY_REQUIRED", clock.instant());
            recovered++;
        }
        return recovered;
    }

    private String classify(RuntimeException failure) {
        return failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                || failure instanceof EntityNotFoundException || failure instanceof BrokerAccountSnapshotIncompleteException
                ? "DETERMINISTIC" : "TRANSPORT_OR_BROKER_UNCERTAIN";
    }

    private void safeEvent(AgentOperationRequestEntity r, String event, String outcome, String correlationId,
                           String actorType, UUID actorId, UUID invocationId, String classification, Instant now) {
        try {
            audit.save(AgentOperationAuditEventEntity.of(r.id(), r.agentId(), r.ownerUserId(), r.operationType().name(),
                    r.resourceId(), event, outcome, correlationId, actorType, actorId, invocationId, classification, now));
        } catch (RuntimeException ignored) {
            // Lifecycle persistence is authoritative; an audit outage must not strand EXECUTING.
        }
    }
}
