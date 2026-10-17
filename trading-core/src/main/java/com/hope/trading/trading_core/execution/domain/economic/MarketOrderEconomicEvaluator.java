package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class MarketOrderEconomicEvaluator {
    public EconomicResult evaluate(
            ReferenceMarketState state,
            StrategyIntent intent,
            BigDecimal takerFeeRate,
            BigDecimal funding,
            CostEvidence feeEvidence,
            CostEvidence fundingEvidence,
            String executionModelVersion
    ) {
        if (state == null || intent == null || takerFeeRate == null || funding == null) {
            return EconomicResult.unavailable(EconomicResultStatus.INSUFFICIENT_DATA,
                    "reference state, strategy intent, fee and funding evidence are required");
        }
        if (feeEvidence == null || fundingEvidence == null) {
            return EconomicResult.unavailable(EconomicResultStatus.DEGRADED,
                    "cost evidence provenance is incomplete");
        }
        if (intent.orderType() != StrategyIntent.OrderType.MARKET) {
            return EconomicResult.unavailable(EconomicResultStatus.UNSUPPORTED,
                    "only market orders are supported by the first simulation policy");
        }
        if (!intent.instrument().equals(state.instrument())) {
            return EconomicResult.unavailable(EconomicResultStatus.INVALID_INPUT,
                    "strategy instrument does not match reference market state");
        }
        if (state.knownAt().isAfter(state.evaluationCutoff())
                || intent.decisionAt().isAfter(state.evaluationCutoff())
                || !feeEvidence.eligibleAt(state.evaluationCutoff())
                || !fundingEvidence.eligibleAt(state.evaluationCutoff())) {
            return EconomicResult.unavailable(EconomicResultStatus.STALE,
                    "market, cost evidence, or decision is not eligible at the evaluation cutoff");
        }
        if (takerFeeRate.signum() < 0) {
            return EconomicResult.unavailable(EconomicResultStatus.INVALID_INPUT,
                    "taker fee rate cannot be negative");
        }
        Objects.requireNonNull(executionModelVersion, "executionModelVersion is required");

        BigDecimal fillPrice = intent.side() == StrategyIntent.Side.BUY ? state.ask() : state.bid();
        BigDecimal referenceNotional = state.referencePrice().multiply(intent.quantity());
        BigDecimal executionNotional = fillPrice.multiply(intent.quantity());
        BigDecimal executionImpact = intent.side() == StrategyIntent.Side.BUY
                ? executionNotional.subtract(referenceNotional)
                : referenceNotional.subtract(executionNotional);
        if (executionImpact.signum() < 0) {
            return EconomicResult.unavailable(EconomicResultStatus.INVALID_INPUT,
                    "reference price produces a negative execution impact");
        }
        BigDecimal fee = executionNotional.multiply(takerFeeRate);
        TransactionCosts costs = new TransactionCosts(executionImpact, fee, funding,
                executionImpact.add(fee).add(funding), feeEvidence, fundingEvidence);
        SimulatedExecution execution = new SimulatedExecution(intent.side(), intent.quantity(), fillPrice,
                state.evaluationCutoff(), executionModelVersion, state.evidenceFingerprint());
        return new EconomicResult(EconomicResultStatus.AVAILABLE, referenceNotional, executionNotional,
                execution, costs, fingerprint(state, intent, execution, costs), null);
    }

    private String fingerprint(ReferenceMarketState state, StrategyIntent intent,
                               SimulatedExecution execution, TransactionCosts costs) {
        String material = String.join("|", state.marketId().toString(), state.instrument(),
                state.evaluationCutoff().toString(), state.evidenceFingerprint(), intent.side().name(),
                intent.quantity().toPlainString(), intent.decisionAt().toString(), execution.fillPrice().toPlainString(),
                execution.executionModelVersion(), costs.executionImpact().toPlainString(),
                costs.fee().toPlainString(), costs.funding().toPlainString(),
                costs.feeEvidence().fingerprint(), costs.fundingEvidence().fingerprint(),
                costs.feeEvidence().knownAt().toString(), costs.fundingEvidence().knownAt().toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
