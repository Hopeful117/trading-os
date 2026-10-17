package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VisibleDepthMarketExecutionCalculator {
    public static final String MODEL_VERSION = "visible-depth-market-execution-v1";
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private final String executionModelVersion;

    public VisibleDepthMarketExecutionCalculator() {
        this(MODEL_VERSION);
    }

    public VisibleDepthMarketExecutionCalculator(String executionModelVersion) {
        this.executionModelVersion = required(executionModelVersion, "executionModelVersion");
    }

    public SimulatedExecution simulate(ReferenceMarketState state, StrategyIntent intent) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(intent, "intent is required");
        if (intent.orderType() != StrategyIntent.OrderType.MARKET) {
            return result(intent, state, BigDecimal.ZERO, null,
                    SimulatedExecutionStatus.UNSUPPORTED_ORDER_TYPE, "only MARKET orders are supported");
        }

        List<ReferenceMarketState.Level> levels = intent.side() == StrategyIntent.Side.BUY
                ? state.asks() : state.bids();
        BigDecimal remaining = intent.quantity();
        BigDecimal filled = BigDecimal.ZERO;
        BigDecimal notional = BigDecimal.ZERO;
        List<SimulatedExecution.Fill> fills = new ArrayList<>();
        Instant executedAt = state.evaluationCutoff();

        for (ReferenceMarketState.Level level : levels) {
            if (remaining.signum() == 0) break;
            if (level.quantity().signum() == 0) continue;
            BigDecimal consumed = remaining.min(level.quantity());
            fills.add(new SimulatedExecution.Fill(consumed, level.price(), executedAt));
            filled = filled.add(consumed);
            notional = notional.add(level.price().multiply(consumed, CALCULATION_CONTEXT));
            remaining = remaining.subtract(consumed);
        }

        if (filled.signum() == 0) {
            return result(intent, state, BigDecimal.ZERO, null,
                    SimulatedExecutionStatus.UNFILLED, "no eligible opposite-side visible liquidity");
        }

        BigDecimal vwap = notional.divide(filled, CALCULATION_CONTEXT);
        SimulatedExecutionStatus status = remaining.signum() == 0
                ? SimulatedExecutionStatus.FILLED : SimulatedExecutionStatus.PARTIALLY_FILLED;
        String reason = remaining.signum() == 0 ? null : "INSUFFICIENT_DEPTH";
        String fingerprint = fingerprint(state, intent, fills, filled, vwap, status, reason);
        return new SimulatedExecution(intent.side(), intent.quantity(), filled, vwap, executedAt, status,
                executionModelVersion, state.evidenceFingerprint(), fills, reason, fingerprint);
    }

    private SimulatedExecution result(StrategyIntent intent, ReferenceMarketState state,
                                      BigDecimal filled, BigDecimal price,
                                      SimulatedExecutionStatus status, String reason) {
        List<SimulatedExecution.Fill> fills = List.of();
        String fingerprint = fingerprint(state, intent, fills, filled, price, status, reason);
        return new SimulatedExecution(intent.side(), intent.quantity(), filled, price,
                state.evaluationCutoff(), status, executionModelVersion, state.evidenceFingerprint(),
                fills, reason, fingerprint);
    }

    private String fingerprint(ReferenceMarketState state, StrategyIntent intent,
                               List<SimulatedExecution.Fill> fills, BigDecimal filled,
                               BigDecimal vwap, SimulatedExecutionStatus status, String reason) {
        List<String> material = new ArrayList<>();
        material.add(state.marketId().toString());
        material.add(state.instrument());
        material.add(state.productType().name());
        material.add(intent.side().name());
        material.add(intent.orderType().name());
        material.add(intent.quantity().toPlainString());
        material.add(intent.decisionAt().toString());
        material.add(state.evaluationCutoff().toString());
        material.add(state.sequenceId() == null ? "" : state.sequenceId().toString());
        material.add(String.valueOf(state.sourceStartOrdinal()));
        material.add(String.valueOf(state.sourceEndOrdinal()));
        material.add(state.normalizationVersion());
        material.add(state.evidenceFingerprint());
        material.add(levelMaterial(state.bids()));
        material.add(levelMaterial(state.asks()));
        material.add(filled.toPlainString());
        material.add(vwap == null ? "" : vwap.toPlainString());
        material.add(status.name());
        material.add(reason == null ? "" : reason);
        material.add(executionModelVersion);
        fills.forEach(fill -> material.add(fill.quantity().toPlainString() + "@"
                + fill.price().toPlainString() + "@" + fill.executedAt()));
        return digest(material);
    }

    private String levelMaterial(List<ReferenceMarketState.Level> levels) {
        StringBuilder result = new StringBuilder();
        for (ReferenceMarketState.Level level : levels) {
            result.append(level.price().toPlainString()).append('@')
                    .append(level.quantity().toPlainString()).append(';');
        }
        return result.toString();
    }

    private String digest(List<String> values) {
        StringBuilder material = new StringBuilder();
        for (String value : values) material.append(value.length()).append(':').append(value);
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
