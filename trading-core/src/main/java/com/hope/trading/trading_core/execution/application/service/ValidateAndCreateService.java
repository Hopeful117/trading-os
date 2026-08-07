package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionValidationException;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;

/**
 * Validates an authorized Trade Plan against authoritative persisted data
 * and creates an Execution Intent from that authoritative data only.
 *
 * <p>This service replaces the previous caller-driven Execution Intent creation.
 * No execution parameters, risk references, or account data are accepted from
 * the caller — all data is loaded from persistence and verified.
 *
 * <p>Execution parameters that cannot be derived from the authoritative Trade Plan
 * are reported as gaps rather than invented.
 */
public final class ValidateAndCreateService {
    private final RiskPersistence riskPersistence;
    private final TradePlanRiskPort tradePlans;
    private final BrokerAccountRepository brokerAccounts;
    private final CreateExecutionIntentService intentCreation;
    private final ExecutionLifecycleService lifecycle;
    private final Clock clock;

    public ValidateAndCreateService(RiskPersistence riskPersistence,
                                     TradePlanRiskPort tradePlans,
                                     BrokerAccountRepository brokerAccounts,
                                     CreateExecutionIntentService intentCreation,
                                     ExecutionLifecycleService lifecycle,
                                     Clock clock) {
        this.riskPersistence = Objects.requireNonNull(riskPersistence);
        this.tradePlans = Objects.requireNonNull(tradePlans);
        this.brokerAccounts = Objects.requireNonNull(brokerAccounts);
        this.intentCreation = Objects.requireNonNull(intentCreation);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutionIntent validateAndCreate(ValidateAndCreateCommand command) {
        Objects.requireNonNull(command, "command is required");

        // 1. Load authoritative RiskEvaluation by ID
        RiskPersistence.StoredEvaluation evaluation = riskPersistence.evaluationById(command.evaluationId())
                .orElseThrow(() -> new ExecutionValidationException("EVALUATION_NOT_FOUND",
                        "Risk Evaluation not found", 404));

        // 2. Verify evaluation completed successfully
        if (!"COMPLETED".equals(evaluation.status())) {
            throw new ExecutionValidationException("EVALUATION_NOT_COMPLETED",
                    "Risk Evaluation has not completed", 422);
        }

        // 3. Verify evaluation decision authorizes execution
        String decision = evaluation.decision();
        if (!"APPROVED".equals(decision) && !"APPROVED_WITH_WARNINGS".equals(decision)) {
            throw new ExecutionValidationException("DECISION_NOT_AUTHORIZED",
                    "Risk decision does not authorize execution", 422);
        }

        // 4. Verify evaluation belongs to the correct account
        if (!evaluation.accountId().equals(command.brokerAccountId())) {
            throw new ExecutionValidationException("EVALUATION_ACCOUNT_MISMATCH",
                    "Risk Evaluation does not match the specified account", 409);
        }

        // 5. Load authoritative TradePlan
        TradePlanRiskPort.Snapshot plan;
        try {
            plan = tradePlans.load(command.tradePlanId(), command.tradePlanVersion());
        } catch (Exception e) {
            throw new ExecutionValidationException("TRADE_PLAN_NOT_FOUND",
                    "Trade Plan not found or unavailable", 404);
        }

        // 6. Verify TradePlan is in accepted state
        if (!"ACCEPTED".equals(plan.status())) {
            throw new ExecutionValidationException("TRADE_PLAN_NOT_ACCEPTED",
                    "Trade Plan is not in accepted state", 422);
        }

        // 7. Verify version correspondence between TradePlan and RiskEvaluation
        if (plan.tradePlanVersion() != evaluation.tradePlanVersion()) {
            throw new ExecutionValidationException("VERSION_MISMATCH",
                    "Trade Plan version does not match Risk Evaluation version", 409);
        }

        // 8. Verify TradePlan identity matches evaluation
        if (!plan.tradePlanId().equals(evaluation.tradePlanId())) {
            throw new ExecutionValidationException("TRADE_PLAN_MISMATCH",
                    "Trade Plan identity does not match Risk Evaluation", 409);
        }

        // 9. Verify account ownership — TradePlan owner must be the authenticated user
        if (!plan.ownerId().equals(command.initiatorId())) {
            throw new ExecutionValidationException("ACCOUNT_FORBIDDEN",
                    "Trade Plan does not belong to the authenticated user", 403);
        }

        // 10. Verify TradingAccount ownership — TradingAccount must belong to the user
        //     Note: TradingAccount is identified by plan.tradingAccountId().
        //     The relationship is: Account.user.userId == initiatorId.
        //     We verify this by checking the RiskEvaluation was created for the same account
        //     that the user owns (which was already verified during risk evaluation).
        //     Additional explicit verification: the evaluation's accountId must match
        //     the plan's tradingAccountId.
        if (!evaluation.accountId().equals(plan.tradingAccountId())) {
            throw new ExecutionValidationException("TRADING_ACCOUNT_MISMATCH",
                    "Risk Evaluation account does not match Trade Plan trading account", 409);
        }

        // 11. Verify BrokerAccount ownership
        brokerAccounts.findByIdAndOwnerId(command.brokerAccountId(), command.initiatorId())
                .orElseThrow(() -> new ExecutionValidationException("BROKER_ACCOUNT_FORBIDDEN",
                        "Broker Account does not belong to the authenticated user", 403));

        // 12. Derive ExecutionParameters from authoritative TradePlan
        ExecutionParameters parameters = deriveParameters(plan);

        // 13. Create ExecutionIntent from authoritative data
        RiskApprovalReference approval = new RiskApprovalReference(
                evaluation.id(),
                RiskApprovalReference.Decision.valueOf(decision),
                evaluation.response().evaluatedAt());

        ExecutionIntent intent = intentCreation.create(
                new com.hope.trading.trading_core.execution.application.command.CreateExecutionIntentCommand(
                        new TradePlanReference(plan.tradePlanId(), plan.tradePlanVersion()),
                        approval,
                        command.idempotencyKey(),
                        command.initiatorId(),
                        command.brokerAccountId(),
                        parameters,
                        command.expiresAt()));

        // 14. Transition to VALIDATED
        lifecycle.validate(intent, clock.instant());

        return intent;
    }

    /**
     * Derives ExecutionParameters from the authoritative TradePlan snapshot.
     *
     * <p>Execution parameters are derived exclusively from the Trade Plan's
     * explicit EntryIntent and positional data. No values are invented.
     *
     * @see EntryIntent
     */
    private ExecutionParameters deriveParameters(TradePlanRiskPort.Snapshot plan) {
        // 1. Validate EntryIntent is present and supported
        EntryIntent entryIntent = plan.entryIntent();
        if (entryIntent == null) {
            throw new ExecutionValidationException("EXECUTION_PARAMETER_GAP",
                    "Trade Plan does not provide an entry intent", 422);
        }

        ExecutionParameters.OrderType orderType = switch (entryIntent.orderType()) {
            case MARKET -> ExecutionParameters.OrderType.MARKET;
            case LIMIT -> ExecutionParameters.OrderType.LIMIT;
            case STOP -> throw new ExecutionValidationException("UNSUPPORTED_ENTRY_INTENT",
                    "Entry intent order type 'STOP' is not yet supported in ExecutionParameters", 422);
        };

        // 2. Validate instrument
        String instrument = plan.instrument();
        if (instrument == null || instrument.isBlank()) {
            throw new ExecutionValidationException("EXECUTION_PARAMETER_GAP",
                    "Trade Plan does not provide instrument", 422);
        }

        // 3. Derive side from direction
        ExecutionParameters.Side side = deriveSide(plan.direction());

        // 4. Validate quantity
        BigDecimal quantity = plan.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new ExecutionValidationException("EXECUTION_PARAMETER_GAP",
                    "Trade Plan does not provide a positive quantity", 422);
        }

        // 5. Build ExecutionParameters from authoritative data only
        return new ExecutionParameters(instrument, side, orderType, quantity, entryIntent.price());
    }

    private ExecutionParameters.Side deriveSide(String direction) {
        if (direction == null) {
            throw new ExecutionValidationException("EXECUTION_PARAMETER_GAP",
                    "Trade Plan does not provide direction", 422);
        }
        return switch (direction.toUpperCase()) {
            case "LONG" -> ExecutionParameters.Side.BUY;
            case "SHORT" -> ExecutionParameters.Side.SELL;
            default -> throw new ExecutionValidationException("EXECUTION_PARAMETER_GAP",
                    "Trade Plan direction '" + direction + "' cannot be mapped to a valid side", 422);
        };
    }
}
