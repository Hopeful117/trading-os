package com.hope.trading.trading_core.risk.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.risk.application.RiskEvaluationException;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Command;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.risk.application.TradePlanRiskEvaluationService;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.security.AgentPrincipal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/trade-plan-risk-evaluations")
public class AgentTradePlanRiskEvaluationController {
    private final TradePlanRiskEvaluationService evaluations;
    private final TradePlanRiskPort plans;
    private final AgentAuthorizationService authorization;
    private final RiskPersistence riskPersistence;
    private final Clock clock;

    public AgentTradePlanRiskEvaluationController(TradePlanRiskEvaluationService evaluations,
                                                   TradePlanRiskPort plans,
                                                   AgentAuthorizationService authorization,
                                                   RiskPersistence riskPersistence,
                                                   Clock clock) {
        this.evaluations = evaluations;
        this.plans = plans;
        this.authorization = authorization;
        this.riskPersistence = riskPersistence;
        this.clock = clock;
    }

    @PostMapping("/{tradePlanId}/versions/{version}")
    public ResponseEntity<Response> evaluate(@PathVariable UUID tradePlanId, @PathVariable long version,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
                                              Authentication authentication) {
        AgentPrincipal agent = requireAgent(authentication);
        if (version < 1 || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new RiskEvaluationException("INVALID_COMMAND", "A valid version and idempotency key are required", 400);
        }
        TradePlanRiskPort.Snapshot plan = plans.load(tradePlanId, version);
        if (!agent.ownerUserId().equals(plan.ownerId())) {
            throw new RiskEvaluationException("TRADE_PLAN_FORBIDDEN", "Trade Plan does not belong to the agent owner", 403);
        }
        UUID brokerAccountId = riskPersistence.configuration(plan.tradingAccountId())
                .map(RiskPersistence.AccountConfiguration::brokerAccountId)
                .orElse(null);
        if (!authorization.authorize(agent.agentId(), AgentPermission.TRADE_PLAN_RISK_EVALUATE,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, correlationId).allowed()) {
            throw new RiskEvaluationException("RISK_EVALUATION_NOT_AUTHORIZED", "Agent is not authorized for this broker account", 403);
        }
        return ResponseEntity.ok(evaluations.evaluate(new Command(agent.ownerUserId(), tradePlanId, version,
                plan.tradingAccountId(), idempotencyKey, clock.instant())));
    }

    private AgentPrincipal requireAgent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal agent)) {
            throw new RiskEvaluationException("AGENT_AUTHENTICATION_REQUIRED", "Authenticated agent is required", 403);
        }
        return agent;
    }
}
