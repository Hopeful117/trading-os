package com.hope.trading.trading_core.execution.application.pipeline;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.application.port.T1ApprovalVerifier;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.ExecutionPurpose;
import com.hope.trading.trading_core.market_data.service.MarketService;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class BrokerSubmissionStep {
    private static final Duration MARKET_STATE_MAX_AGE = Duration.ofSeconds(30);
    private final BrokerExecutionPort broker;
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final ExecutionLifecycleService lifecycle;
    private final T1ApprovalVerifier t1ApprovalVerifier;
    private final MarketService markets;
    private final BrokerAccountRepository accounts;
    private final Clock clock;
    public BrokerSubmissionStep(BrokerExecutionPort broker,
            ExecutionIntentRepositoryPort intents, ExecutionAttemptRepositoryPort attempts,
            ExecutionLifecycleService lifecycle, T1ApprovalVerifier t1ApprovalVerifier) {
        this(broker, intents, attempts, lifecycle, t1ApprovalVerifier, null, null, null);
    }
    public BrokerSubmissionStep(BrokerExecutionPort broker,
            ExecutionIntentRepositoryPort intents, ExecutionAttemptRepositoryPort attempts,
            ExecutionLifecycleService lifecycle, T1ApprovalVerifier t1ApprovalVerifier,
            MarketService markets, Clock clock) {
        this(broker, intents, attempts, lifecycle, t1ApprovalVerifier, markets, null, clock);
    }
    public BrokerSubmissionStep(BrokerExecutionPort broker,
            ExecutionIntentRepositoryPort intents, ExecutionAttemptRepositoryPort attempts,
            ExecutionLifecycleService lifecycle, T1ApprovalVerifier t1ApprovalVerifier,
            MarketService markets, BrokerAccountRepository accounts, Clock clock) {
        this.broker = Objects.requireNonNull(broker); this.intents = Objects.requireNonNull(intents);
        this.attempts = Objects.requireNonNull(attempts);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.t1ApprovalVerifier = Objects.requireNonNull(t1ApprovalVerifier);
        this.markets = markets;
        this.accounts = accounts;
        this.clock = clock;
    }
    public void execute(ExecutionPipelineContext context) {
        if (context.intent().purpose() == ExecutionPurpose.ENTRY
                && (!context.t1Approved()
                || context.t1EvaluationId() == null
                || context.attempt() == null
                || !context.t1EvaluationId().equals(context.attempt().t1EvaluationId())
                || !t1ApprovalVerifier.isApproved(context.t1EvaluationId(), context.intent().id().value()))) {
            throw new InvalidExecutionStateException("T1 risk approval is required before broker submission");
        }
        Optional<MarketResponse> cTraderMarket = currentCTraderMarket(context);
        requireCurrentTradability(cTraderMarket);
        lifecycle.start(context.intent(), context.attempt(), context.now());
        attempts.save(context.attempt());
        intents.save(context.intent());
        context.submissionResult(broker.submit(new BrokerExecutionPort.ExecutionRequest(
                context.intent().id(), context.attempt().id(),
                context.intent().idempotencyKey(), context.intent().brokerAccountId(),
                context.intent().parameters(), cTraderMarket.map(this::evidence).orElse(null))));
    }

    private Optional<MarketResponse> currentCTraderMarket(ExecutionPipelineContext context) {
        if (markets == null || clock == null) return Optional.empty();
        if (accounts != null && accounts.findById(context.intent().brokerAccountId())
                .map(account -> account.executionMode() == ExecutionMode.PAPER)
                .orElse(false)) {
            return Optional.empty();
        }
        String instrument = context.intent().parameters().instrument().toUpperCase(Locale.ROOT);
        return markets.findAll().stream()
                .filter(market -> market.getProvider() == MarketProvider.CTRADER)
                .filter(market -> instrument.equals(market.getSymbol().toUpperCase(Locale.ROOT)))
                .findFirst();
    }

    private void requireCurrentTradability(Optional<MarketResponse> market) {
        market.ifPresent(value -> {
            if (!isCurrentlyTradable(value)) {
                throw new InvalidExecutionStateException(
                        "cTrader market tradability is unknown, closed, or stale");
            }
        });
    }

    private BrokerExecutionPort.MarketTradabilityEvidence evidence(MarketResponse market) {
        var state = market.getMarketState();
        return new BrokerExecutionPort.MarketTradabilityEvidence(
                state.getTradingStatus().name(), state.isTradable(), state.getLastUpdated());
    }

    private boolean isCurrentlyTradable(MarketResponse market) {
        var state = market.getMarketState();
        return state != null && state.isTradable()
                && state.getTradingStatus() == com.hope.trading.trading_core.market_data.helper.TradingStatus.OPEN
                && state.getLastUpdated() != null
                && !state.getLastUpdated().isBefore(clock.instant().minus(MARKET_STATE_MAX_AGE));
    }
}
