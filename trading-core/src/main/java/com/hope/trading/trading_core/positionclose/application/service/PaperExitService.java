package com.hope.trading.trading_core.positionclose.application.service;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.service.CreateExecutionIntentService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.BrokerOrderRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class PaperExitService {
    private final AccountRepository accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final CreateExecutionIntentService intentCreation;
    private final ExecutionIntentRepositoryPort intents;
    private final ExecuteTradeService execution;
    private final BrokerOrderRepositoryPort orders;
    private final Clock clock;

    public PaperExitService(AccountRepository accounts, BrokerAccountRepository brokerAccounts,
                            CreateExecutionIntentService intentCreation, ExecutionIntentRepositoryPort intents,
                            ExecuteTradeService execution, BrokerOrderRepositoryPort orders, Clock clock) {
        this.accounts = accounts; this.brokerAccounts = brokerAccounts; this.intentCreation = intentCreation;
        this.intents = intents; this.execution = execution; this.orders = orders; this.clock = clock;
    }

    @Transactional
    public PaperExitResponse close(UUID userId, UUID accountId, UUID tradeId, String idempotencyKey) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new PaperExitException("POSITION_NOT_FOUND"));
        if (account.getUser() == null || !userId.equals(account.getUser().getUserId())) {
            throw new PaperExitException("POSITION_NOT_OWNED");
        }
        if (account.getBrokerAccountId() == null) throw new PaperExitException("CANONICAL_RELATION_MISSING");
        var broker = brokerAccounts.findByIdAndOwnerId(account.getBrokerAccountId(), userId)
                .orElseThrow(() -> new PaperExitException("BROKER_ACCOUNT_FORBIDDEN"));
        if (broker.executionMode() != ExecutionMode.PAPER) throw new PaperExitException("EXECUTION_MODE_MISMATCH");
        var existing = intents.findByIdempotencyKey(new IdempotencyKey(idempotencyKey));
        if (existing.isPresent()) {
            ExecutionIntent prior = existing.get();
            if (prior.purpose() != com.hope.trading.trading_core.execution.domain.model.ExecutionPurpose.EXIT
                    || !userId.equals(prior.initiatorId()) || !broker.id().equals(prior.brokerAccountId())
                    || !tradeId.equals(prior.targetTradeId().orElse(null))) {
                throw new PaperExitException("IDEMPOTENCY_KEY_CONFLICT");
            }
            return response(prior);
        }
        Trade trade = account.getTrades().stream().filter(t -> tradeId.equals(t.getTradeId())).findFirst()
                .orElseThrow(() -> new PaperExitException("POSITION_NOT_FOUND"));
        if (trade.getTradeStatus() != TradeStatus.OPEN || trade.getClosedAt() != null) {
            throw new PaperExitException("POSITION_ALREADY_CLOSED");
        }
        ExecutionParameters.Side side = trade.getType() == com.hope.trading.trading_core.helper.TradeType.BUY
                ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY;
        ExecutionIntent intent = intentCreation.createExit(userId, broker.id(), tradeId,
                new ExecutionParameters(trade.getSymbol(), side, ExecutionParameters.OrderType.MARKET,
                        trade.getQuantity(), null), new IdempotencyKey(idempotencyKey), clock.instant().plusSeconds(300));
        intent = execution.execute(intent.id());
        return response(intent);
    }

    private PaperExitResponse response(ExecutionIntent intent) {
        String external = null;
        var order = orders.findByIntentId(intent.id());
        if (order.isPresent()) external = order.get().externalOrderId();
        String status = switch (intent.status()) {
            case COMPLETED -> "CLOSED";
            case FAILED, RISK_REVALIDATION_REJECTED -> "REJECTED";
            default -> intent.status().name();
        };
        return new PaperExitResponse(intent.id().value(), status, external, null, null, null);
    }

    public static final class PaperExitResponse {
        public final UUID id;
        public final String status;
        public final String externalOrderId;
        public final String failureReason;
        public final String resolvedMutationScope;
        public final String reconciliationResult;
        public PaperExitResponse(UUID id, String status, String externalOrderId, String failureReason,
                                 String resolvedMutationScope, String reconciliationResult) {
            this.id = id; this.status = status; this.externalOrderId = externalOrderId;
            this.failureReason = failureReason; this.resolvedMutationScope = resolvedMutationScope;
            this.reconciliationResult = reconciliationResult;
        }
    }

    public static class PaperExitException extends RuntimeException {
        public PaperExitException(String code) { super(code); }
    }
}
