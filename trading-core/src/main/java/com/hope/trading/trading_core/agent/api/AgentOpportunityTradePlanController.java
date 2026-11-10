package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/trade-plans/opportunities")
public class AgentOpportunityTradePlanController {
    private final BrokerAccountRepository brokerAccounts;
    private final AccountRepository accounts;
    private final AgentAuthorizationService authorization;
    private final OpportunityTradePlanOrchestrationService orchestration;

    public AgentOpportunityTradePlanController(
            BrokerAccountRepository brokerAccounts,
            AccountRepository accounts,
            AgentAuthorizationService authorization,
            OpportunityTradePlanOrchestrationService orchestration) {
        this.brokerAccounts = brokerAccounts;
        this.accounts = accounts;
        this.authorization = authorization;
        this.orchestration = orchestration;
    }

    @PostMapping("/{opportunityId}")
    public ResponseEntity<Response> create(
            @PathVariable UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Request request,
            Authentication authentication,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        AgentPrincipal agent = requireAgent(authentication);
        BrokerAccount brokerAccount = brokerAccounts.findByIdAndOwnerId(
                        request.brokerAccountId(), agent.ownerUserId())
                .orElseThrow(AgentBusinessAuthorizationException::new);
        if (!authorization.authorize(agent.agentId(), AgentPermission.PLANS_CREATE,
                AgentResourceType.BROKER_ACCOUNT, brokerAccount.id(), correlationId).allowed()) {
            throw new AgentBusinessAuthorizationException();
        }
        UUID accountId = accounts.findByBrokerAccountId(brokerAccount.id())
                .filter(account -> account.getUser() != null
                        && agent.ownerUserId().equals(account.getUser().getUserId()))
                .map(Account::getAccountId)
                .orElseThrow(AgentBusinessAuthorizationException::new);
        var result = orchestration.createFromOpportunity(agent.ownerUserId(), opportunityId,
                request.opportunityVersion(), accountId, idempotencyKey);
        return ResponseEntity.ok(new Response(result.tradePlanId(), result.tradePlanVersion(), result.reused()));
    }

    private AgentPrincipal requireAgent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal agent)) {
            throw new AgentBusinessAuthorizationException();
        }
        return agent;
    }

    public record Request(@NotNull UUID brokerAccountId, @NotNull Long opportunityVersion) { }

    public record Response(UUID tradePlanId, long tradePlanVersion, boolean reused) { }
}

@RestControllerAdvice(assignableTypes = AgentOpportunityTradePlanController.class)
class AgentOpportunityTradePlanExceptionHandler {
    @ExceptionHandler(AgentBusinessAuthorizationException.class)
    ResponseEntity<Map<String, String>> authorizationDenied(AgentBusinessAuthorizationException failure) {
        return ResponseEntity.status(403).body(Map.of(
                "code", "AGENT_BUSINESS_AUTHORIZATION_DENIED",
                "message", failure.getMessage()));
    }

    @ExceptionHandler(com.hope.trading.trading_core.tradeplanning.application.AnalysisTradePlanGenerationException.class)
    ResponseEntity<Map<String, Object>> planningFailure(
            com.hope.trading.trading_core.tradeplanning.application.AnalysisTradePlanGenerationException failure) {
        var response = new java.util.LinkedHashMap<String, Object>();
        response.put("code", failure.code());
        response.put("message", failure.getMessage());
        response.putAll(failure.details());
        return ResponseEntity.status(failure.status()).body(response);
    }
}
