package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.port.HistoricalOrderBookPort;
import com.hope.trading.trading_core.execution.domain.economic.EconomicProductType;
import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecution;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.VisibleDepthMarketExecutionCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public final class HistoricalMarketExecutionService {
    private static final int BOUNDED_DEPTH = 25;
    private final HistoricalOrderBookPort orderBook;
    private final VisibleDepthMarketExecutionCalculator calculator;

    @Autowired
    public HistoricalMarketExecutionService(HistoricalOrderBookPort orderBook) {
        this(orderBook, new VisibleDepthMarketExecutionCalculator());
    }

    public HistoricalMarketExecutionService(HistoricalOrderBookPort orderBook,
                                            VisibleDepthMarketExecutionCalculator calculator) {
        this.orderBook = orderBook;
        this.calculator = calculator;
    }

    public SimulatedExecution execute(UUID marketId, StrategyIntent intent, Instant executionCutoff) {
        if (marketId == null || intent == null || executionCutoff == null) {
            throw new IllegalArgumentException("marketId, intent and executionCutoff are required");
        }
        if (intent.orderType() != StrategyIntent.OrderType.MARKET) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.UNSUPPORTED_ORDER_TYPE,
                    "only MARKET orders are supported");
        }
        if (!intent.decisionAt().equals(executionCutoff)) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.INVALID_INPUT,
                    "zero-latency execution requires cutoff to equal decisionAt");
        }

        HistoricalOrderBookPort.Replay replay;
        try {
            replay = orderBook.replay(marketId, BOUNDED_DEPTH, executionCutoff);
        } catch (RuntimeException exception) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.INSUFFICIENT_MARKET_EVIDENCE,
                    "historical Order Book replay is unavailable");
        }
        if (replay == null || replay.status() != HistoricalOrderBookPort.Status.AVAILABLE) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.INSUFFICIENT_MARKET_EVIDENCE,
                    replay == null || replay.reason() == null ? "historical Order Book replay is unavailable" : replay.reason());
        }
        if (!hasCompleteReplayIdentity(replay, marketId)
                || replay.evaluationCutoff() == null || !executionCutoff.equals(replay.evaluationCutoff())
                || replay.sequenceId() == null || replay.latestOccurredAt() == null
                || replay.latestKnownAt() == null || replay.sourceStartOrdinal() == null
                || replay.sourceEndOrdinal() == null || replay.sourceStartOrdinal() < 0
                || replay.sourceEndOrdinal() < replay.sourceStartOrdinal()
                || !hasText(replay.provider()) || !hasText(replay.providerSymbol())
                || !hasText(replay.normalizationVersion()) || !hasText(replay.sourceEvidenceFingerprint())) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.INVALID_INPUT,
                    "historical replay provenance is incomplete");
        }
        if (replay.latestOccurredAt().isAfter(executionCutoff) || replay.latestKnownAt().isAfter(executionCutoff)) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.STALE_EVIDENCE,
                    "historical replay contains evidence known after the execution cutoff");
        }

        List<ReferenceMarketState.Level> bids = replay.bids().stream()
                .map(level -> new ReferenceMarketState.Level(level.price(), level.quantity())).toList();
        List<ReferenceMarketState.Level> asks = replay.asks().stream()
                .map(level -> new ReferenceMarketState.Level(level.price(), level.quantity())).toList();
        BigDecimal bid = bids.isEmpty() ? null : bids.get(0).price();
        BigDecimal ask = asks.isEmpty() ? null : asks.get(0).price();
        if (bid != null && ask != null && bid.compareTo(ask) > 0) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.INVALID_INPUT,
                    "historical replay has no valid non-crossed top of book");
        }
        BigDecimal referencePrice = bid != null && ask != null
                ? bid.add(ask).divide(BigDecimal.valueOf(2)) : bid != null ? bid : ask;
        if (referencePrice == null) {
            return unavailable(marketId, intent, executionCutoff, SimulatedExecutionStatus.UNFILLED,
                    "historical replay contains no executable visible liquidity");
        }
        ReferenceMarketState state = new ReferenceMarketState(marketId, intent.instrument(), executionCutoff,
                bid, ask, referencePrice, EconomicProductType.LINEAR_PERPETUAL,
                replay.latestKnownAt(),
                replay.sourceEvidenceFingerprint(), bids, asks, replay.sequenceId(), replay.sourceStartOrdinal(),
                replay.sourceEndOrdinal(), replay.provider(), replay.providerSymbol(), replay.normalizationVersion());
        return calculator.simulate(state, intent);
    }

    private boolean hasCompleteReplayIdentity(HistoricalOrderBookPort.Replay replay, UUID requestedMarketId) {
        return replay.marketId() != null && requestedMarketId.equals(replay.marketId()) && replay.depth() == BOUNDED_DEPTH;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private SimulatedExecution unavailable(UUID marketId, StrategyIntent intent, Instant cutoff,
                                           SimulatedExecutionStatus status, String reason) {
        String source = "historical-order-book:" + status.name();
        return new SimulatedExecution(intent.side(), intent.quantity(), BigDecimal.ZERO, null, cutoff, status,
                VisibleDepthMarketExecutionCalculator.MODEL_VERSION, source, List.of(), reason,
                digest(source + "|marketId=" + marketId + "|instrument=" + intent.instrument()
                        + "|reason=" + reason + "|side=" + intent.side() + "|quantity=" + intent.quantity()
                        + "|cutoff=" + cutoff));
    }

    private String digest(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
