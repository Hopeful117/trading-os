package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.valuation.application.PaperDerivativeAccountValuationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import feign.RetryableException;

@Component
public final class ModeAwareRiskFactsProvider implements RiskFactsProvider {
    private final BrokerRiskFactsPort liveFacts;
    private final BrokerAccountSnapshotV2Client authoritativeSnapshots;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final PaperDerivativeAccountValuationService derivativeValuation;

    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper) {
        this(liveFacts, null, mapper, Clock.systemUTC(), null);
    }

    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper, Clock clock) {
        this(liveFacts, null, mapper, clock, null);
    }

    @Autowired
    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, BrokerAccountSnapshotV2Client authoritativeSnapshots,
                                      ObjectMapper mapper, Clock clock,
                                      PaperDerivativeAccountValuationService derivativeValuation) {
        this.liveFacts = liveFacts;
        this.authoritativeSnapshots = authoritativeSnapshots;
        this.mapper = mapper;
        this.clock = clock;
        this.derivativeValuation = derivativeValuation;
    }

    public ModeAwareRiskFactsProvider(BrokerRiskFactsPort liveFacts, ObjectMapper mapper, Clock clock,
                                      PaperDerivativeAccountValuationService derivativeValuation) {
        this(liveFacts, null, mapper, clock, derivativeValuation);
    }

    @Override
    public RiskFactsProvider.Snapshot load(com.hope.trading.trading_core.model.Account account,
                                           BrokerAccount brokerAccount, UUID sourceId,
                                             Instant from, Instant to) {
        if (brokerAccount.executionMode() == ExecutionMode.LIVE) {
            if (authoritativeSnapshots != null) {
                BrokerAccountSnapshotV2Dto authoritative = null;
                try {
                    authoritative = authoritativeSnapshots.getAccountSnapshot(sourceId);
                } catch (RetryableException ignored) {
                    // The v2 compatibility route is unavailable for Kraken in
                    // this state. Its established RiskSnapshot route remains
                    // the fallback; cTrader has no legacy Risk capability.
                }
                if (authoritative != null) {
                    validateIdentity(brokerAccount, sourceId, authoritative);
                    if (authoritative.account() != null) {
                        return fromAuthoritative(authoritative, from, to);
                    }
                }
            }
            return RiskFactsProvider.fromBroker(liveFacts.load(sourceId, from, to));
        }
        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            return paperSnapshot(account, brokerAccount, from, to);
        }
        throw new IllegalStateException("Execution mode is unavailable");
    }

    private void validateIdentity(BrokerAccount brokerAccount, UUID sourceId,
                                  BrokerAccountSnapshotV2Dto snapshot) {
        if (!sourceId.equals(snapshot.brokerAccountId())
                || !brokerAccount.providerRegistrationReference().equals(snapshot.providerRegistrationReference())
                || (brokerAccount.externalAccountId() != null
                && !brokerAccount.externalAccountId().equals(snapshot.externalAccountId()))) {
            throw new IllegalStateException("Authoritative broker snapshot identity mismatch");
        }
    }

    private RiskFactsProvider.Snapshot fromAuthoritative(BrokerAccountSnapshotV2Dto snapshot,
                                                          Instant from, Instant to) {
        List<String> reasons = new java.util.ArrayList<>(snapshot.unavailabilityReasons() == null
                ? List.of() : snapshot.unavailabilityReasons());
        boolean complete = "COMPLETE".equals(snapshot.completeness())
                && "FRESH".equals(snapshot.freshness());
        if (!complete) reasons.add("AUTHORITATIVE_SNAPSHOT_NOT_COMPLETE_AND_FRESH");

        var account = snapshot.account();
        if (account.valuationAsset() == null || account.balance() == null
                || account.equity() == null || account.usedMargin() == null) {
            complete = false;
            reasons.add("ACCOUNT_RISK_FACTS_INCOMPLETE");
        }
        if (snapshot.positions() != null) {
            for (var position : snapshot.positions()) {
                if (position.margin() == null) {
                    complete = false;
                    reasons.add("POSITION_MARGIN_UNAVAILABLE:" + position.brokerPositionReference());
                }
            }
        }

        List<RiskFactsProvider.Position> positions = snapshot.positions() == null ? List.of()
                : snapshot.positions().stream().map(position -> new RiskFactsProvider.Position(
                        UUID.nameUUIDFromBytes((sourceId(snapshot) + ":" + position.brokerPositionReference())
                                .getBytes(StandardCharsets.UTF_8)),
                        position.brokerPositionReference(), "BROKER_AUTHORITATIVE_V2", position.instrument(),
                        position.signedQuantity(), position.entryPrice(), null, position.margin(),
                        position.stopLoss() == null ? BigDecimal.ZERO : position.signedQuantity().abs(),
                        position.stopLoss() == null ? List.of() : List.of(new RiskFactsProvider.Stop(
                                 position.brokerPositionReference() + ":stop-loss",
                                 "BROKER_POSITION_STOP_LOSS", position.signedQuantity().abs(), position.stopLoss()))))
                         .toList();
        List<RiskFactsProvider.ClosedTrade> closedTrades = snapshot.deals() == null ? List.of()
                : snapshot.deals().stream().map(deal -> new RiskFactsProvider.ClosedTrade(
                        deal.providerTradeReference(), deal.instrument(), deal.settlementAsset(), deal.fee(),
                        deal.realizedPnl(), deal.closedAt()))
                .filter(deal -> deal.closedAt() == null
                        || (!deal.closedAt().isBefore(from) && deal.closedAt().isBefore(to)))
                .toList();
        long sourceVersion = snapshot.completedAt() == null ? 0 : snapshot.completedAt().toEpochMilli();
        return new RiskFactsProvider.Snapshot(snapshot.brokerAccountId(), sourceVersion, snapshot.observedAt(),
                complete, List.copyOf(new java.util.LinkedHashSet<>(reasons)), snapshot.balances(),
                new RiskFactsProvider.Account(account.valuationAsset(), account.balance(), account.equity(),
                        account.usedMargin(), null), positions, closedTrades, List.of(), writePayload(snapshot));
    }

    private String sourceId(BrokerAccountSnapshotV2Dto snapshot) {
        return snapshot.brokerAccountId().toString();
    }

    private String writePayload(BrokerAccountSnapshotV2Dto snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Authoritative broker snapshot cannot be preserved", failure);
        }
    }

    private RiskFactsProvider.Snapshot paperSnapshot(com.hope.trading.trading_core.model.Account account,
                                                        BrokerAccount brokerAccount,
                                                          Instant from, Instant to) {
        List<String> reasons = new java.util.ArrayList<>();
        if (account == null || account.getAccountId() == null || blank(account.getBaseCurrency())) {
            reasons.add("PAPER_ACCOUNT_FACTS_INVALID");
        }
        String valuationAsset = account == null || blank(account.getBaseCurrency())
                ? null : account.getBaseCurrency().toUpperCase(Locale.ROOT);
        List<com.hope.trading.trading_core.model.AccountBalance> accountBalances = account == null
                || account.getBalances() == null ? List.of() : account.getBalances();
        Map<String, BigDecimal> balances = accountBalances.stream()
                .filter(value -> value != null && !blank(value.getAsset()) && value.getAmount() != null)
                .collect(Collectors.toMap(value -> value.getAsset().toUpperCase(Locale.ROOT),
                        value -> value.getAmount(), BigDecimal::add));
        if (accountBalances.stream().anyMatch(value -> value == null || blank(value.getAsset())
                || value.getAmount() == null)) {
            reasons.add("PAPER_BALANCE_FACTS_INVALID");
        }
        refreshCashValuation(account, reasons);
        List<Trade> trades = account == null || account.getTrades() == null ? List.of() : account.getTrades();
        List<RiskFactsProvider.Position> positions = trades.stream()
                .filter(trade -> trade != null && trade.getTradeStatus() == TradeStatus.OPEN)
                .map(this::paperPosition)
                .toList();
        List<RiskFactsProvider.ClosedTrade> closedTrades = trades.stream()
                .filter(trade -> trade != null && trade.getTradeStatus() == TradeStatus.CLOSED
                        && trade.getClosedAt() != null
                        && !trade.getClosedAt().isBefore(from)
                        && trade.getClosedAt().isBefore(to))
                .map(trade -> paperClosedTrade(trade, valuationAsset))
                .toList();
        if (trades.stream().anyMatch(this::invalidTradeFacts)) {
            reasons.add("PAPER_TRADE_FACTS_INVALID");
        }
        BigDecimal balance = account != null && account.getSettlementModel() == SettlementModel.CASH
                ? account.getRealizedBalance()
                : valuationAsset == null ? null : balances.get(valuationAsset);
        if (balance == null || account.getEquity() == null) {
            reasons.add("PAPER_ACCOUNT_VALUATION_INCOMPLETE");
        }
        String payload = writePayload(account, balances, positions, closedTrades);
        return new RiskFactsProvider.Snapshot(
                brokerAccount.id(),
                sourceVersion(account, trades),
                clock.instant(),
                reasons.isEmpty(),
                reasons,
                balances,
                new RiskFactsProvider.Account(valuationAsset, balance, account == null ? null : account.getEquity(),
                        BigDecimal.ZERO, account == null ? null : account.getStartingBalance()),
                positions,
                closedTrades,
                List.of(),
                payload);
    }

    private void refreshCashValuation(com.hope.trading.trading_core.model.Account account,
                                      List<String> reasons) {
        if (account == null || account.getSettlementModel() != SettlementModel.CASH) return;
        if (derivativeValuation == null) {
            reasons.add("PAPER_CASH_VALUATION_UNAVAILABLE");
            return;
        }
        try {
            PaperDerivativeAccountValuationService.ValuationResult valuation =
                    derivativeValuation.refresh(account);
            if (!valuation.fresh()) reasons.add("PAPER_CASH_VALUATION_NOT_FRESH");
        } catch (RuntimeException failure) {
            reasons.add("PAPER_CASH_VALUATION_UNAVAILABLE");
        }
    }

    private RiskFactsProvider.Position paperPosition(Trade trade) {
        BigDecimal quantity = signedQuantity(trade);
        BigDecimal absoluteQuantity = quantity == null ? null : quantity.abs();
        BigDecimal currentPrice = trade.getCurrentPrice() == null ? trade.getEntryPrice() : trade.getCurrentPrice();
        List<RiskFactsProvider.Stop> stops = trade.getStopLoss() == null || absoluteQuantity == null ? List.of()
                : List.of(new RiskFactsProvider.Stop("paper-trade-stop:" + trade.getTradeId(),
                        "TRADING_CORE", absoluteQuantity, trade.getStopLoss()));
        return new RiskFactsProvider.Position(trade.getTradeId(), "paper-trade:" + trade.getTradeId(),
                "TRADING_CORE", trade.getSymbol(), quantity, trade.getEntryPrice(),
                currentPrice == null || absoluteQuantity == null ? null : currentPrice.multiply(absoluteQuantity),
                trade.getEntryPrice() == null || absoluteQuantity == null
                        ? null : trade.getEntryPrice().multiply(absoluteQuantity),
                trade.getStopLoss() == null ? BigDecimal.ZERO : absoluteQuantity, stops);
    }

    private RiskFactsProvider.ClosedTrade paperClosedTrade(Trade trade, String settlementAsset) {
        return new RiskFactsProvider.ClosedTrade("paper-trade:" + trade.getTradeId(), trade.getSymbol(),
                settlementAsset, BigDecimal.ZERO, trade.getPnl() == null ? BigDecimal.ZERO : trade.getPnl(),
                trade.getClosedAt());
    }

    private BigDecimal signedQuantity(Trade trade) {
        if (trade == null || trade.getType() == null || trade.getQuantity() == null) return null;
        return trade.getType().name().equals("BUY") ? trade.getQuantity() : trade.getQuantity().negate();
    }

    private String writePayload(com.hope.trading.trading_core.model.Account account,
                                Map<String, BigDecimal> balances,
                                List<RiskFactsProvider.Position> positions,
                                List<RiskFactsProvider.ClosedTrade> closedTrades) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("accountId", account == null ? null : account.getAccountId());
            payload.put("accountVersion", account == null ? null : account.getVersion());
            payload.put("tradeVersions", account == null || account.getTrades() == null ? Map.of()
                    : account.getTrades().stream().filter(trade -> trade != null && trade.getTradeId() != null)
                            .collect(Collectors.toMap(Trade::getTradeId, Trade::getVersion)));
            payload.put("balances", balances);
            payload.put("positions", positions);
            payload.put("closedTrades", closedTrades);
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Paper risk snapshot cannot be preserved", failure);
        }
    }

    private long sourceVersion(com.hope.trading.trading_core.model.Account account, List<Trade> trades) {
        long accountVersion = account == null ? 0 : account.getVersion();
        long tradeVersion = trades.stream().filter(java.util.Objects::nonNull)
                .mapToLong(Trade::getVersion).max().orElse(0);
        long version = Math.max(accountVersion, tradeVersion);
        return account == null ? 0 : Math.max(1, version);
    }

    private boolean invalidTradeFacts(Trade trade) {
        return trade == null || trade.getTradeId() == null || blank(trade.getSymbol())
                || trade.getType() == null || trade.getQuantity() == null
                || trade.getQuantity().signum() <= 0 || trade.getEntryPrice() == null
                || trade.getEntryPrice().signum() <= 0 || trade.getTradeStatus() == null
                || (trade.getCurrentPrice() != null && trade.getCurrentPrice().signum() <= 0)
                || (trade.getTradeStatus() == TradeStatus.OPEN
                    && trade.getStopLoss() != null && trade.getStopLoss().signum() <= 0)
                || (trade.getTradeStatus() == TradeStatus.CLOSED
                    && (trade.getClosedAt() == null || trade.getPnl() == null));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
