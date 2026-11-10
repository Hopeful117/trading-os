package com.hope.trading.trading_core.tradeplanning.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.*;
import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class TradePlanApprovalService {
    private final TradePlanApprovalChallengeRepository challenges;
    private final AgentAuthorizationService authorization;
    private final OpportunityTradePlanOrchestrationService plans;
    private final RiskPersistence risk;
    private final AccountRepository accounts;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Value("${trading-core.trade-plan-approval.ttl-seconds:300}") private long ttlSeconds;

    public TradePlanApprovalService(TradePlanApprovalChallengeRepository challenges, AgentAuthorizationService authorization,
            OpportunityTradePlanOrchestrationService plans, RiskPersistence risk, AccountRepository accounts,
            ObjectMapper mapper, Clock clock) {
        this.challenges = challenges; this.authorization = authorization; this.plans = plans;
        this.risk = risk; this.accounts = accounts; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public TradePlanApprovalDtos.Response request(AgentPrincipal agent, TradePlanApprovalDtos.Request input, String idempotencyKey, String correlationId) {
        if (input == null || input.tradePlanId() == null || input.riskEvaluationId() == null || input.tradePlanVersion() < 1)
            throw failure(400, "INVALID_APPROVAL_REQUEST");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200)
            throw failure(400, "IDEMPOTENCY_KEY_REQUIRED");
        PlanTransport plan = plans.load(agent.ownerUserId(), input.tradePlanId(), input.tradePlanVersion());
        var evaluation = validEvaluation(input, plan);
        var decision = authorization.authorize(agent.agentId(), AgentPermission.TRADE_PLAN_APPROVAL_REQUEST,
                AgentResourceType.BROKER_ACCOUNT, plan.tradingAccountId(), correlationId);
        if (!decision.allowed()) throw failure(403, "APPROVAL_NOT_AUTHORIZED");
        var existing = challenges.findByAgentIdAndIdempotencyKey(agent.agentId(), idempotencyKey);
        if (existing.isPresent()) {
            var stored = existing.get();
            if (!stored.tradePlanId().equals(plan.id()) || stored.tradePlanVersion() != plan.version()
                    || !stored.riskEvaluationId().equals(input.riskEvaluationId())) throw failure(409, "IDEMPOTENCY_KEY_REUSED");
            return response(stored, plan, evaluation);
        }
        Instant now = clock.instant();
        String digest = digest(plan);
        var challenge = TradePlanApprovalChallengeEntity.create(agent.agentId(), agent.ownerUserId(), plan.tradingAccountId(),
                plan.id(), plan.version(), evaluation.id(), digest, idempotencyKey, now, now.plusSeconds(Math.max(30, ttlSeconds)));
        challenges.save(challenge);
        return response(challenge, plan, evaluation);
    }

    @Transactional(readOnly = true)
    public TradePlanApprovalDtos.Response agentGet(AgentPrincipal agent, UUID id, String correlationId) {
        var challenge = challenges.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() -> failure(404, "NOT_FOUND"));
        if (!agent.ownerUserId().equals(challenge.ownerUserId())) throw failure(403, "OWNER_MISMATCH");
        authorize(agent, challenge, AgentPermission.TRADE_PLAN_APPROVAL_STATUS_READ, correlationId);
        return response(challenge, plans.load(agent.ownerUserId(), challenge.tradePlanId(), challenge.tradePlanVersion()),
                risk.evaluationById(challenge.riskEvaluationId()).orElseThrow(() -> failure(404, "RISK_EVALUATION_NOT_FOUND")));
    }

    @Transactional(readOnly = true)
    public PlanTransport agentPlan(AgentPrincipal agent, UUID brokerAccountId, UUID planId, long version, String correlationId) {
        var decision = authorization.authorize(agent.agentId(), AgentPermission.TRADE_PLAN_APPROVAL_STATUS_READ,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, correlationId);
        if (!decision.allowed()) throw failure(403, "APPROVAL_NOT_AUTHORIZED");
        UUID accountId = accounts.findByBrokerAccountId(brokerAccountId)
                .filter(account -> account.getUser() != null && agent.ownerUserId().equals(account.getUser().getUserId()))
                .map(com.hope.trading.trading_core.model.Account::getAccountId)
                .orElseThrow(() -> failure(403, "ACCOUNT_MISMATCH"));
        PlanTransport plan = plans.load(agent.ownerUserId(), planId, version);
        if (!accountId.equals(plan.tradingAccountId())) throw failure(403, "ACCOUNT_MISMATCH");
        return plan;
    }

    @Transactional
    public TradePlanApprovalDtos.Response humanGet(UUID ownerId, UUID id) {
        var challenge = current(challenges.findByIdAndOwnerUserId(id, ownerId).orElseThrow(() -> failure(404, "NOT_FOUND")));
        return response(challenge, plans.load(ownerId, challenge.tradePlanId(), challenge.tradePlanVersion()),
                risk.evaluationById(challenge.riskEvaluationId()).orElseThrow(() -> failure(404, "RISK_EVALUATION_NOT_FOUND")));
    }

    @Transactional
    public TradePlanApprovalDtos.Response decide(UUID ownerId, UUID id, boolean approve) {
        var challenge = current(challenges.findByIdAndOwnerUserId(id, ownerId).orElseThrow(() -> failure(404, "NOT_FOUND")));
        PlanTransport plan = plans.load(ownerId, challenge.tradePlanId(), challenge.tradePlanVersion());
        var evaluation = validEvaluation(new TradePlanApprovalDtos.Request(challenge.tradePlanId(), challenge.tradePlanVersion(), challenge.riskEvaluationId()), plan);
        if (!challenge.parameterDigest().equals(digest(plan))) throw failure(409, "PARAMETERS_CHANGED");
        if (approve) plans.decide(ownerId, challenge.tradePlanId(), challenge.tradePlanVersion(), "ACCEPT");
        else plans.decide(ownerId, challenge.tradePlanId(), challenge.tradePlanVersion(), "REJECT");
        Instant now = clock.instant();
        if (approve) challenge.approve(ownerId, now); else challenge.reject(ownerId, now);
        challenges.saveAndFlush(challenge);
        return response(challenge, plans.load(ownerId, challenge.tradePlanId(), challenge.tradePlanVersion()), evaluation);
    }

    private TradePlanApprovalChallengeEntity current(TradePlanApprovalChallengeEntity challenge) {
        if (challenge.status() != TradePlanApprovalStatus.PENDING) throw failure(409, "CHALLENGE_NOT_PENDING");
        if (!challenge.expiresAt().isAfter(clock.instant())) { challenge.expire(); challenges.save(challenge); throw failure(409, "CHALLENGE_EXPIRED"); }
        return challenge;
    }

    private RiskPersistence.StoredEvaluation validEvaluation(TradePlanApprovalDtos.Request request, PlanTransport plan) {
        var evaluation = risk.evaluationById(request.riskEvaluationId()).orElseThrow(() -> failure(404, "RISK_EVALUATION_NOT_FOUND"));
        if (!Objects.equals(evaluation.tradePlanId(), plan.id()) || !Objects.equals(evaluation.tradePlanVersion(), plan.version())
                || !Objects.equals(evaluation.accountId(), plan.tradingAccountId()) || !"PRE_TRADE".equals(evaluation.evaluationMode())
                || !"COMPLETED".equals(evaluation.status()) || evaluation.response() == null || !evaluation.response().approved())
            throw failure(409, "RISK_EVALUATION_NOT_APPROVING");
        return evaluation;
    }

    private void authorize(AgentPrincipal agent, TradePlanApprovalChallengeEntity challenge, AgentPermission permission, String correlationId) {
        var result = authorization.authorize(agent.agentId(), permission, AgentResourceType.BROKER_ACCOUNT,
                challenge.tradingAccountId(), correlationId);
        if (!result.allowed()) throw failure(403, "APPROVAL_NOT_AUTHORIZED");
    }

    private String digest(PlanTransport plan) {
        try {
            var values = new LinkedHashMap<String, Object>();
            values.put("id", plan.id()); values.put("version", plan.version()); values.put("account", plan.tradingAccountId());
            values.put("instrument", plan.instrument()); values.put("direction", plan.direction()); values.put("entryType", plan.entryType());
            values.put("entryPrice", plan.entryPrice()); values.put("stopLoss", plan.stopLoss()); values.put("takeProfits", plan.takeProfits());
            values.put("quantity", plan.quantity()); values.put("notional", plan.notional()); values.put("monetaryRisk", plan.monetaryRisk());
            return hex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(values)));
        } catch (Exception error) { throw new IllegalStateException("approval digest failed", error); }
    }
    private String hex(byte[] bytes) { var out = new StringBuilder(bytes.length * 2); for (byte value : bytes) out.append(String.format("%02x", value)); return out.toString(); }
    private TradePlanApprovalDtos.Response response(TradePlanApprovalChallengeEntity challenge, PlanTransport plan, RiskPersistence.StoredEvaluation evaluation) {
        return new TradePlanApprovalDtos.Response(challenge.id(), challenge.status(), challenge.ownerUserId(), challenge.tradingAccountId(),
                challenge.tradePlanId(), challenge.tradePlanVersion(), challenge.riskEvaluationId(), challenge.expiresAt(),
                challenge.parameterDigest(), plan, TradePlanApprovalDtos.RiskSummary.of(evaluation), challenge.reason());
    }
    private AgentOperationException failure(int status, String code) { return new AgentOperationException(status, code); }
}
