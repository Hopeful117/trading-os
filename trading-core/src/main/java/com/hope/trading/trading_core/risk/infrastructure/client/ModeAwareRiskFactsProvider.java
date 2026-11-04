package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountFactsV2Dto;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactRequirements;
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
        return load(account, brokerAccount, sourceId, from, to, null);
    }

    @Override
    public RiskFactsProvider.Snapshot load(com.hope.trading.trading_core.model.Account account,
                                           BrokerAccount brokerAccount, UUID sourceId,
                                           Instant from, Instant to, RiskFactRequirements requirements) {
        if (brokerAccount.executionMode() == ExecutionMode.LIVE) {
            if (authoritativeSnapshots != null) {
                BrokerAccountSnapshotV2Dto authoritative = null;
                try {
                    UUID actorId = account.getUser() == null ? null : account.getUser().getUserId();
                    authoritative = actorId == null
                            ? authoritativeSnapshots.getAccountSnapshot(sourceId)
                            : authoritativeSnapshots.getAccountSnapshot(actorId, sourceId);
                } catch (RetryableException ignored) {
                    // The v2 compatibility route is unavailable for Kraken in
                    // this state. Its established RiskSnapshot route remains
                    // the fallback; cTrader has no legacy Risk capability.
                }
                if (authoritative != null) {
                    validateIdentity(brokerAccount, sourceId, authoritative);
                    if (authoritative.account() != null) {
                        return fromAuthoritative(authoritative, account, from, to, requirements);
                    }
                }
            }
            return RiskFactsProvider.fromBroker(liveFacts.load(sourceId, from, to), requirements);
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
                                                          com.hope.trading.trading_core.model.Account localAccount,
                                                          Instant from, Instant to,
                                                          RiskFactRequirements requirements) {
        List<String> reasons = new java.util.ArrayList<>(snapshot.unavailabilityReasons() == null
                ? List.of() : snapshot.unavailabilityReasons());
        boolean complete = contextuallyComplete(snapshot, localAccount, requirements);
        if (!complete && requirements == null) {
            reasons.add("AUTHORITATIVE_SNAPSHOT_NOT_COMPLETE_AND_FRESH");
        } else if (!complete && "FRESH".equals(snapshot.freshness())) {
            reasons.add("AUTHORITATIVE_FACT_REQUIREMENTS_UNAVAILABLE");
        }

        var account = snapshot.account();
        if (account == null) {
            complete = false;
            reasons.add("ACCOUNT_FACTS_UNAVAILABLE");
            return new RiskFactsProvider.Snapshot(snapshot.brokerAccountId(),
                    snapshot.completedAt() == null ? 0 : snapshot.completedAt().toEpochMilli(),
                    snapshot.observedAt(), false, List.copyOf(new java.util.LinkedHashSet<>(reasons)),
                    snapshot.balances(), null, List.of(), List.of(), List.of(), writePayload(snapshot),
                    dealAvailability(snapshot.dealAvailability(), snapshot.deals()));
        }

        RiskFactsProvider.EquityFact equityFact = equityFact(account.equityFact(), snapshot);
        if (requirements != null && requirements.equity()
                && !equityAdmissible(equityFact, requirements, account.equity(), account.valuationAsset())) {
            reasons.add(equityFact.reasons().isEmpty()
                    ? "EQUITY_PROVENANCE_UNAVAILABLE" : equityFact.reasons().getFirst());
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
                        account.usedMargin(), null, equityFact), positions, closedTrades, List.of(), writePayload(snapshot),
                dealAvailability(snapshot.dealAvailability(), snapshot.deals()));
    }

    private RiskFactsProvider.EquityFact equityFact(
            BrokerAccountFactsV2Dto.EquityFactDto value, BrokerAccountSnapshotV2Dto snapshot) {
        if (value == null || value.provenance() == null || value.availability() == null) {
            return RiskFactsProvider.EquityFact.unavailable("EQUITY_PROVENANCE_UNAVAILABLE");
        }
        try {
            RiskFactRequirements.EquityProvenance provenance =
                    RiskFactRequirements.EquityProvenance.valueOf(value.provenance());
            return new RiskFactsProvider.EquityFact(value.value(), value.currency(), provenance,
                    value.availability(), value.observedAt(), value.capturedAt(), value.fresh(),
                    value.complete(), value.coherent(), value.source(), value.resolutionMethod(), value.reasons());
        } catch (RuntimeException invalid) {
            return RiskFactsProvider.EquityFact.unavailable("EQUITY_PROVENANCE_INVALID");
        }
    }

    private boolean contextuallyComplete(BrokerAccountSnapshotV2Dto snapshot,
                                         com.hope.trading.trading_core.model.Account localAccount,
                                         RiskFactRequirements requirements) {
        if (snapshot == null || snapshot.account() == null
                || snapshot.observedAt() == null || snapshot.completedAt() == null
                || snapshot.completedAt().toEpochMilli() < 1
                || !"FRESH".equals(snapshot.freshness())
                || snapshot.balances() == null || snapshot.positions() == null
                || snapshot.deals() == null || snapshot.positionPnls() == null) {
            return false;
        }
        if (requirements == null) return "COMPLETE".equals(snapshot.completeness());

        var account = snapshot.account();
        if (requirements.equity() && !equityAdmissible(equityFact(account.equityFact(), snapshot), requirements,
                account.equity(), account.valuationAsset())) return false;
        if (requirements.balance() && account.balance() == null) return false;
        if (requirements.equity() && account.equity() == null) return false;
        if (requirements.usedMargin() && account.usedMargin() == null) return false;
        if (requirements.freeMargin() && account.freeMargin() == null) return false;
        if (requirements.positions()
                && snapshot.positionAvailability() == com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT) {
            return false;
        }
        if (requirements.closedDeals()
                && (snapshot.dealAvailability() == com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT
                || snapshot.dealAvailability() == com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability.TRUNCATED)) {
            return false;
        }
        if (requirements.startingBalance()
                && (localAccount == null || localAccount.getStartingBalance() == null)) {
            return false;
        }
        return snapshot.unavailabilityReasons() == null
                || snapshot.unavailabilityReasons().stream().allMatch(reason -> optionalOrRequiredReason(reason, requirements));
    }

    private boolean equityAdmissible(RiskFactsProvider.EquityFact equity,
                                     RiskFactRequirements requirements, BigDecimal numericEquity,
                                     String valuationAsset) {
        if (!requirements.equityContractRequired()) return numericEquity != null;
        return equity != null
                && "AVAILABLE".equals(equity.availability())
                && equity.value() != null && numericEquity != null
                && equity.value().compareTo(numericEquity) == 0
                && equity.currency() != null && equity.currency().equalsIgnoreCase(valuationAsset)
                && equity.observedAt() != null && equity.capturedAt() != null
                && equity.fresh() && equity.complete() && equity.coherent()
                && requirements.admissibleEquityProvenances().contains(equity.provenance());
    }

    private boolean optionalOrRequiredReason(String reason, RiskFactRequirements requirements) {
        if (reason == null || reason.isBlank() || "OBSERVATION_INCOMPLETE".equals(reason)) return true;
        if (reason.startsWith("EQUITY_PROVENANCE") || reason.startsWith("EQUITY_QUALITY")) {
            return !requirements.equity();
        }
        if ("EQUITY_UNAVAILABLE".equals(reason)) return !requirements.equity();
        if ("USED_MARGIN_UNAVAILABLE".equals(reason)) return !requirements.usedMargin();
        if ("FREE_MARGIN_UNAVAILABLE".equals(reason)) return !requirements.freeMargin();
        if ("DEAL_HISTORY_UNAVAILABLE".equals(reason) || "DEAL_HISTORY_TRUNCATED".equals(reason)) {
            return !requirements.closedDeals();
        }
        if ("UNREALIZED_PNL_UNAVAILABLE".equals(reason)) return true;
        if (reason.startsWith("DEAL_")) return !requirements.closedDeals();
        if (reason.startsWith("ORDER_") || reason.equals("RESPONSE_FIELD_UNAVAILABLE:order")) return true;
        if (reason.equals("RESPONSE_FIELD_UNAVAILABLE:position")) return !requirements.positions();
        if (reason.startsWith("POSITION_MARGIN_UNAVAILABLE")) return !requirements.positions();
        if (reason.startsWith("POSITION_")) return !requirements.positions();
        return false;
    }

    private RiskFactsProvider.DealAvailability dealAvailability(
            com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability availability,
            List<?> deals) {
        if (availability == null) {
            return deals == null || deals.isEmpty()
                    ? RiskFactsProvider.DealAvailability.AVAILABLE_EMPTY
                    : RiskFactsProvider.DealAvailability.AVAILABLE_NON_EMPTY;
        }
        return switch (availability) {
            case AVAILABLE_EMPTY -> RiskFactsProvider.DealAvailability.AVAILABLE_EMPTY;
            case AVAILABLE_NON_EMPTY -> RiskFactsProvider.DealAvailability.AVAILABLE_NON_EMPTY;
            case TRUNCATED -> RiskFactsProvider.DealAvailability.TRUNCATED;
            case UNAVAILABLE_OR_ABSENT -> RiskFactsProvider.DealAvailability.UNAVAILABLE_OR_ABSENT;
        };
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
                        BigDecimal.ZERO, account == null ? null : account.getStartingBalance(),
                        account == null || account.getEquity() == null
                                ? RiskFactsProvider.EquityFact.unavailable("PAPER_ACCOUNT_VALUATION_INCOMPLETE")
                                : validPaperEquity(account.getEquity(), valuationAsset)),
                positions,
                closedTrades,
                List.of(),
                payload);
    }

    private RiskFactsProvider.EquityFact validPaperEquity(BigDecimal equity, String currency) {
        if (currency == null || currency.isBlank()) {
            return RiskFactsProvider.EquityFact.unavailable("PAPER_VALUATION_CURRENCY_UNAVAILABLE");
        }
        return RiskFactsProvider.EquityFact.available(equity, currency, clock.instant(), clock.instant(),
                "PAPER_ACCOUNT_STATE", "PAPER_MARK_TO_MARKET");
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
