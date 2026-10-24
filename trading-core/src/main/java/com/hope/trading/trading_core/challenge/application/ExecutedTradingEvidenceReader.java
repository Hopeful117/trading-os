package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ExecutedTradingEvidence;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore.Provenance;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.risk.application.RiskDay;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExecutedTradingEvidenceReader {
    private final TradeRepository trades;
    private final TradeExecutionProvenanceStore provenance;
    private final Clock clock;

    public ExecutedTradingEvidenceReader(TradeRepository trades,
                                         TradeExecutionProvenanceStore provenance,
                                         Clock clock) {
        this.trades = trades;
        this.provenance = provenance;
        this.clock = clock;
    }

    public ExecutedTradingEvidence read(ChallengeInstance challenge) {
        return read(challenge, clock.instant());
    }

    public ExecutedTradingEvidence read(ChallengeInstance challenge, Instant observedAt) {
        if (challenge.startedAt() == null || challenge.riskTimeZone() == null
                || challenge.riskDayResetTime() == null || observedAt.isBefore(challenge.startedAt())) {
            return new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.CONTRADICTORY,
                    Set.of(), observedAt, "");
        }

        try {
            ZoneId.of(challenge.riskTimeZone());
            LocalTime reset = challenge.riskDayResetTime();
            List<UUID> tradeIds = trades.findAllByAccount_AccountId(challenge.accountId()).stream()
                    .map(trade -> trade.getTradeId())
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (tradeIds.isEmpty()) {
                return complete(challenge, observedAt, Set.of(), 0);
            }

            var tradesById = trades.findAllByAccount_AccountId(challenge.accountId()).stream()
                    .filter(trade -> trade.getTradeId() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            com.hope.trading.trading_core.model.Trade::getTradeId,
                            java.util.function.Function.identity(),
                            (left, right) -> left));
            List<Provenance> records = provenance.findAllByTradeIdsAndExecutedAtBetween(
                    tradeIds, challenge.startedAt(), observedAt);
            Set<java.time.LocalDate> days = new HashSet<>();
            for (Provenance record : records) {
                var trade = tradesById.get(record.tradeId());
                if (!"PAPER".equals(record.executionMode())
                        || !"ENTRY".equals(record.executionPurpose())
                        || record.executedAt() == null || trade == null || trade.getOpenedAt() == null
                        || record.executionAttemptId() == null || record.brokerOrderId() == null
                        || record.fillId() == null || record.fillId().isBlank()) {
                    return new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.CONTRADICTORY,
                            Set.of(), observedAt, "");
                }
                days.add(RiskDay.containing(record.executedAt(), challenge.riskTimeZone(), reset).date());
            }
            return complete(challenge, observedAt, days, records.size());
        } catch (DateTimeException | IllegalArgumentException ex) {
            return new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.CONTRADICTORY,
                    Set.of(), observedAt, "");
        }
    }

    private ExecutedTradingEvidence complete(ChallengeInstance challenge, Instant observedAt,
                                              Set<java.time.LocalDate> days, int recordCount) {
        return new ExecutedTradingEvidence(ExecutedTradingEvidence.Status.COMPLETE, days,
                observedAt, "paper-trade-execution-provenance:" + challenge.id() + ":" + recordCount);
    }
}
