package com.hope.trading.trading_core.executionauthorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.application.service.QueryExecutionService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ValidateAndCreateService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class ExecutionAuthorizationService {
    private final ExecutionAuthorizationRepository authorizations;
    private final ExecutionAuthorizationAuditRepository audit;
    private final AgentAuthorizationService agentAuthorization;
    private final OpportunityTradePlanOrchestrationService plans;
    private final RiskPersistence risk;
    private final ValidateAndCreateService validateAndCreate;
    private final QueryExecutionService executions;
    private final ExecuteTradeService executeTrade;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String webUrl;
    private final long ttlSeconds;
    private final boolean enabled;

    public ExecutionAuthorizationService(ExecutionAuthorizationRepository authorizations,
            ExecutionAuthorizationAuditRepository audit, AgentAuthorizationService agentAuthorization,
            OpportunityTradePlanOrchestrationService plans, RiskPersistence risk,
            ValidateAndCreateService validateAndCreate, QueryExecutionService executions,
            ExecuteTradeService executeTrade, UserRepository users, PasswordEncoder passwords, ObjectMapper mapper, Clock clock,
            @Value("${trading-core.web-url:http://localhost:4200}") String webUrl,
            @Value("${trading-core.execution-authorization.ttl-seconds:300}") long ttlSeconds,
            @Value("${trading-core.execution-authorization.enabled:false}") boolean enabled) {
        this.authorizations = authorizations;
        this.audit = audit;
        this.agentAuthorization = agentAuthorization;
        this.plans = plans;
        this.risk = risk;
        this.validateAndCreate = validateAndCreate;
        this.executions = executions;
        this.executeTrade = executeTrade;
        this.users = users;
        this.passwords = passwords;
        this.mapper = mapper;
        this.clock = clock;
        this.webUrl = webUrl;
        this.ttlSeconds = Math.max(30, Math.min(ttlSeconds, 300));
        this.enabled = enabled;
    }

    @Transactional
    public ExecutionAuthorizationDtos.Response request(AgentPrincipal agent,
            ExecutionAuthorizationDtos.Request request, String correlationId) {
        ensureEnabled();
        if (request == null || request.tradePlanId() == null || request.riskEvaluationId() == null
                || request.brokerAccountId() == null || request.executionIdempotencyKey() == null
                || request.executionIdempotencyKey().isBlank() || request.tradePlanVersion() < 1) {
            throw failure(400, "INVALID_EXECUTION_AUTHORIZATION_REQUEST");
        }
        var decision = agentAuthorization.authorize(agent.agentId(), AgentPermission.EXECUTIONS_REQUEST,
                AgentResourceType.BROKER_ACCOUNT, request.brokerAccountId(), correlationId);
        if (!decision.allowed()) throw failure(403, "EXECUTION_AUTHORIZATION_NOT_AUTHORIZED");
        PlanTransport plan = plans.load(agent.ownerUserId(), request.tradePlanId(), request.tradePlanVersion());
        UUID canonicalMarketId = plan.originContext() == null ? null : plan.originContext().marketId();
        if (canonicalMarketId == null || plan.instrumentProvider() == null || plan.instrumentProvider().isBlank()
                || plan.instrumentProductType() == null || plan.instrumentProductType().isBlank()
                || plan.instrument() == null || plan.instrument().isBlank()) {
            throw failure(409, "EXECUTION_MARKET_IDENTITY_UNAVAILABLE");
        }
        var evaluation = risk.evaluationById(request.riskEvaluationId())
                .orElseThrow(() -> failure(404, "RISK_EVALUATION_NOT_FOUND"));
        if (!plan.id().equals(evaluation.tradePlanId()) || plan.version() != evaluation.tradePlanVersion()
                || !plan.tradingAccountId().equals(evaluation.accountId())
                || !request.brokerAccountId().equals(risk.configuration(plan.tradingAccountId())
                        .map(RiskPersistence.AccountConfiguration::brokerAccountId).orElse(null))
                || evaluation.response() == null || !evaluation.response().approved()
                || !"COMPLETED".equals(evaluation.status()) || !"PRE_TRADE".equals(evaluation.evaluationMode())) {
            throw failure(409, "RISK_OR_PLAN_MISMATCH");
        }
        var existing = authorizations.findByAgentIdAndExecutionIdempotencyKey(
                agent.agentId(), request.executionIdempotencyKey());
        if (existing.isPresent()) {
            var value = existing.get();
            if (!value.requestFingerprint().equals(fingerprint(agent, decision, request, plan))) {
                throw failure(409, "IDEMPOTENCY_KEY_REUSED");
            }
            return response(value, plan);
        }
        Instant now = clock.instant();
        var authorization = ExecutionAuthorizationEntity.create(agent.ownerUserId(), agent.agentId(),
                decision.grantId(), decision.grantVersion(), request.brokerAccountId(), plan.tradingAccountId(),
                plan.instrumentProvider(), plan.instrumentProductType() + ":" + plan.instrument(), canonicalMarketId, plan.id(),
                plan.version(), request.riskEvaluationId(), request.executionIdempotencyKey(),
                fingerprint(agent, decision, request, plan), now, now.plusSeconds(ttlSeconds));
        try {
            authorizations.saveAndFlush(authorization);
        } catch (DataIntegrityViolationException conflict) {
            throw failure(409, "IDEMPOTENCY_CONFLICT_RETRY");
        }
        record(authorization, agent.ownerUserId(), agent.agentId(), "REQUESTED", "SUCCESS", correlationId, now);
        return response(authorization, plan);
    }

    @Transactional
    public ExecutionAuthorizationDtos.Response agentGet(AgentPrincipal agent, UUID id, String correlationId) {
        ensureEnabled();
        var value = authorizations.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() -> failure(404, "NOT_FOUND"));
        authorizeAgent(agent, value, AgentPermission.EXECUTIONS_STATUS_READ, correlationId);
        expireIfNeeded(value, agent.ownerUserId(), agent.agentId(), correlationId);
        return response(value, plans.load(agent.ownerUserId(), value.tradePlanId(), value.tradePlanVersion()));
    }

    @Transactional(readOnly = true)
    public ExecutionIntent agentExecutionGet(AgentPrincipal agent, UUID id, UUID brokerAccountId,
            String correlationId) {
        ensureEnabled();
        var decision = agentAuthorization.authorize(agent.agentId(), AgentPermission.EXECUTIONS_STATUS_READ,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, correlationId);
        if (!decision.allowed()) throw failure(403, "EXECUTION_STATUS_NOT_AUTHORIZED");
        return executions.find(new com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId(id))
                .filter(value -> agent.ownerUserId().equals(value.initiatorId())
                        && brokerAccountId.equals(value.brokerAccountId()))
                .orElseThrow(() -> failure(404, "NOT_FOUND"));
    }

    @Transactional
    public ExecutionAuthorizationDtos.Response humanGet(UUID userId, UUID id) {
        ensureEnabled();
        var value = authorizations.findByIdAndHumanUserId(id, userId).orElseThrow(() -> failure(404, "NOT_FOUND"));
        expireIfNeeded(value, userId, null, null);
        return response(value, plans.load(userId, value.tradePlanId(), value.tradePlanVersion()));
    }

    @Transactional
    public ExecutionAuthorizationDtos.Response decide(UUID userId, UUID id, boolean approve,
            String password, String correlationId) {
        ensureEnabled();
        var value = authorizations.findByIdAndHumanUserId(id, userId).orElseThrow(() -> failure(404, "NOT_FOUND"));
        ensurePassword(userId, password);
        if (!value.expiresAt().isAfter(clock.instant())) {
            value.expire();
            authorizations.saveAndFlush(value);
            throw failure(409, "EXECUTION_AUTHORIZATION_EXPIRED");
        }
        if (value.status() != ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION) {
            throw failure(409, "EXECUTION_AUTHORIZATION_NOT_PENDING");
        }
        Instant now = clock.instant();
        if (approve) value.approve(userId, now); else value.reject(userId, now);
        record(value, userId, null, approve ? "APPROVED" : "REJECTED", "SUCCESS", correlationId, now);
        authorizations.saveAndFlush(value);
        return response(value, plans.load(userId, value.tradePlanId(), value.tradePlanVersion()));
    }

    @Transactional
    public ExecutionAuthorizationDtos.Response revoke(UUID userId, UUID id, String password, String correlationId) {
        ensureEnabled();
        var value = authorizations.findByIdAndHumanUserId(id, userId).orElseThrow(() -> failure(404, "NOT_FOUND"));
        ensurePassword(userId, password);
        if (value.status() != ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION
                && value.status() != ExecutionAuthorizationStatus.APPROVED) {
            throw failure(409, "EXECUTION_AUTHORIZATION_NOT_REVOCABLE");
        }
        Instant now = clock.instant();
        value.revoke(now);
        record(value, userId, null, "REVOKED", "SUCCESS", correlationId, now);
        authorizations.saveAndFlush(value);
        return response(value, plans.load(userId, value.tradePlanId(), value.tradePlanVersion()));
    }

    @Transactional
    public ExecutionIntent continueApproved(AgentPrincipal agent, UUID id, String key, String correlationId) {
        ensureEnabled();
        var value = authorizations.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() -> failure(404, "NOT_FOUND"));
        authorizeAgent(agent, value, AgentPermission.EXECUTIONS_CONTINUE_APPROVED, correlationId);
        if (!value.executionIdempotencyKey().equals(key)) throw failure(409, "IDEMPOTENCY_KEY_MISMATCH");
        if (value.status() == ExecutionAuthorizationStatus.CONSUMED && value.executionIntentId() != null) {
            return executions.find(new com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId(value.executionIntentId()))
                    .orElseThrow(() -> failure(409, "EXECUTION_INTENT_NOT_FOUND"));
        }
        if (!value.expiresAt().isAfter(clock.instant())) {
            value.expire();
            authorizations.saveAndFlush(value);
            throw failure(409, "EXECUTION_AUTHORIZATION_EXPIRED");
        }
        if (value.status() != ExecutionAuthorizationStatus.APPROVED) throw failure(409, "EXECUTION_AUTHORIZATION_NOT_APPROVED");
        value.beginConsumption();
        authorizations.saveAndFlush(value);
        IdempotencyKey executionKey = new IdempotencyKey(value.executionIdempotencyKey());
        ExecutionIntent intent = executions.findByIdempotencyKey(executionKey).orElseGet(() ->
                validateAndCreate.validateAndCreate(new ValidateAndCreateCommand(
                        value.humanUserId(), value.tradePlanId(), value.tradePlanVersion(), value.riskEvaluationId(),
                        value.brokerAccountId(), executionKey, value.expiresAt())));
        verifyIntent(value, intent);
        if (!intent.status().terminal()) {
            intent = executeTrade.execute(intent.id());
        }
        Instant now = clock.instant();
        value.consume(agent.agentId(), intent.id().value(), now);
        record(value, value.humanUserId(), agent.agentId(), "CONSUMED", "SUCCESS", correlationId, now);
        authorizations.saveAndFlush(value);
        return intent;
    }

    private void ensurePassword(UUID userId, String password) {
        if (password == null || password.isBlank() || users.findById(userId)
                .filter(user -> passwords.matches(password, user.getPassword())).isEmpty()) {
            throw failure(401, "STEP_UP_AUTHENTICATION_FAILED");
        }
    }
    private void ensureEnabled() {
        if (!enabled) throw failure(404, "EXECUTION_AUTHORIZATION_NOT_AVAILABLE");
    }
    private void expireIfNeeded(ExecutionAuthorizationEntity value, UUID userId, UUID agentId,
            String correlationId) {
        if (value.expiresAt().isAfter(clock.instant())
                || (value.status() != ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION
                && value.status() != ExecutionAuthorizationStatus.APPROVED)) return;
        Instant now = clock.instant();
        value.expire();
        authorizations.saveAndFlush(value);
        record(value, userId, agentId, "EXPIRED", "SUCCESS", correlationId, now);
    }
    private void verifyIntent(ExecutionAuthorizationEntity value, ExecutionIntent intent) {
        if (!value.humanUserId().equals(intent.initiatorId())
                || !value.brokerAccountId().equals(intent.brokerAccountId())
                || !value.tradingAccountId().equals(intent.accountId())
                || !value.executionIdempotencyKey().equals(intent.idempotencyKey().value())
                || intent.tradePlan() == null
                || !value.tradePlanId().equals(intent.tradePlan().tradePlanId())
                || value.tradePlanVersion() != intent.tradePlan().version()
                || intent.riskApproval() == null
                || !value.riskEvaluationId().equals(intent.riskApproval().evaluationId())) {
            throw failure(409, "EXECUTION_INTENT_MISMATCH");
        }
    }
    private void authorizeAgent(AgentPrincipal agent, ExecutionAuthorizationEntity value,
            AgentPermission permission, String correlationId) {
        if (!agent.ownerUserId().equals(value.humanUserId())) throw failure(403, "OWNER_MISMATCH");
        var decision = agentAuthorization.authorize(agent.agentId(), permission,
                AgentResourceType.BROKER_ACCOUNT, value.brokerAccountId(), correlationId);
        if (!decision.allowed()) throw failure(403, "EXECUTION_AUTHORIZATION_NOT_AUTHORIZED");
    }
    private String fingerprint(AgentPrincipal agent, AgentAuthorizationService.Decision decision,
            ExecutionAuthorizationDtos.Request request, PlanTransport plan) {
        try {
            var values = new LinkedHashMap<String, Object>();
            values.put("owner", agent.ownerUserId()); values.put("agent", agent.agentId());
            values.put("grant", decision.grantId()); values.put("grantVersion", decision.grantVersion());
            values.put("brokerAccount", request.brokerAccountId()); values.put("tradingAccount", plan.tradingAccountId());
            values.put("provider", plan.instrumentProvider()); values.put("product", plan.instrumentProductType());
            values.put("market", plan.instrument()); values.put("plan", plan.id()); values.put("version", plan.version());
            values.put("canonicalMarketId", plan.originContext() == null ? null : plan.originContext().marketId());
            values.put("risk", request.riskEvaluationId()); values.put("key", request.executionIdempotencyKey());
            values.put("direction", plan.direction()); values.put("entryType", plan.entryType());
            values.put("entryPrice", plan.entryPrice()); values.put("quantity", plan.quantity());
            values.put("stopLoss", plan.stopLoss()); values.put("takeProfits", plan.takeProfits());
            return hex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(values)));
        } catch (Exception error) { throw new IllegalStateException("execution authorization fingerprint failed", error); }
    }
    private void record(ExecutionAuthorizationEntity value, UUID userId, UUID agentId, String event,
            String outcome, String correlationId, Instant now) {
        audit.saveAndFlush(ExecutionAuthorizationAuditEntity.of(value.id(), userId, agentId,
                event, outcome, correlationId == null ? null : correlationId.strip(), now));
    }
    private ExecutionAuthorizationDtos.Response response(ExecutionAuthorizationEntity value, PlanTransport plan) {
        return new ExecutionAuthorizationDtos.Response(value.id(), value.status(), value.humanUserId(), value.agentId(),
                value.brokerAccountId(), value.tradingAccountId(), value.tradePlanId(), value.tradePlanVersion(),
                value.riskEvaluationId(), value.canonicalMarketId(), value.requestFingerprint(), webUrl.replaceAll("/$", "")
                        + "/execution-authorizations/" + value.id(), value.expiresAt(), value.executionIntentId(), plan);
    }
    private String hex(byte[] bytes) { var out = new StringBuilder(bytes.length * 2); for (byte value : bytes) out.append(String.format("%02x", value)); return out.toString(); }
    private AgentOperationException failure(int status, String code) { return new AgentOperationException(status, code); }
}
