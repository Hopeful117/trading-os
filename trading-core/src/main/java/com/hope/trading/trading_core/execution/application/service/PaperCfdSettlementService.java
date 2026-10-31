package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.shared.domain.financial.CfdFinancialCalculator;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.valuation.application.CfdValuationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dedicated local accounting boundary for PAPER CFD exposure lifecycle. */
@Service
public class PaperCfdSettlementService {
    private final BrokerAccountRepository brokerAccounts;
    private final AccountRepository accounts;
    private final MarketDataClient marketData;
    private final TradeExecutionProvenanceStore provenance;
    private final CfdValuationService valuation;
    private final Clock clock;
    private final CfdFinancialCalculator calculator = new CfdFinancialCalculator();

    public PaperCfdSettlementService(BrokerAccountRepository brokerAccounts,
                                     AccountRepository accounts,
                                     MarketDataClient marketData,
                                     TradeExecutionProvenanceStore provenance,
                                     CfdValuationService valuation,
                                     Clock clock) {
        this.brokerAccounts = brokerAccounts;
        this.accounts = accounts;
        this.marketData = marketData;
        this.provenance = provenance;
        this.valuation = valuation;
        this.clock = clock;
    }

    public boolean supports(ExecutionIntent intent) {
        if (intent == null || intent.parameters() == null || marketData == null) return false;
        return Optional.ofNullable(marketData.findAll()).orElse(List.of()).stream()
                .anyMatch(market -> market != null
                        && intent.parameters().instrument().equalsIgnoreCase(market.getSymbol())
                        && market.getProductType() == InstrumentProductType.CFD);
    }

    @Transactional
    public Account refreshEquity(Account account) {
        requirePaperCashAccount(account);
        ensureCashBaseline(account);
        Projection projection = project(account, null);
        account.setEquity(account.getRealizedBalance().add(projection.unrealized()));
        account.setValuationStatus(projection.status());
        account.setValuedAt(clock.instant());
        account.setMarketDataTimestamp(projection.marketDataTimestamp());
        accounts.save(account);
        return account;
    }

    @Transactional
    public UUID settle(ExecutionIntent intent, ExecutionAttempt attempt, BrokerOrder order) {
        if (intent == null || order == null || attempt == null) {
            throw new IllegalArgumentException("PAPER CFD settlement context is required");
        }
        var brokerAccount = brokerAccounts.findById(intent.brokerAccountId())
                .orElseThrow(() -> new IllegalStateException("PAPER broker account is unavailable"));
        if (brokerAccount.executionMode() != ExecutionMode.PAPER) {
            throw new IllegalStateException("PAPER CFD settlement rejects LIVE context");
        }
        Account account = accounts.findByBrokerAccountId(brokerAccount.id())
                .orElseThrow(() -> new IllegalStateException("PAPER account is unavailable"));
        requirePaperCashAccount(account);
        if (account.getUser() == null || !brokerAccount.ownerId().equals(account.getUser().getUserId())) {
            throw new IllegalStateException("PAPER account identity is inconsistent");
        }

        BrokerOrder.Fill fill = fill(order);
        validateFill(intent, fill);
        Optional<TradeExecutionProvenanceStore.Provenance> replay =
                provenance.findByBrokerOrderIdAndFillId(order.id().value(), fill.fillId());
        if (replay.isPresent()) return replay.get().tradeId();

        return intent.purpose() == com.hope.trading.trading_core.execution.domain.model.ExecutionPurpose.ENTRY
                ? entry(account, intent, attempt, order, fill)
                : exit(account, intent, attempt, order, fill);
    }

    private UUID entry(Account account, ExecutionIntent intent, ExecutionAttempt attempt,
                       BrokerOrder order, BrokerOrder.Fill fill) {
        InstrumentFinancialContract contract = resolveContract(intent.parameters().instrument());
        BigDecimal signedQuantity = signed(intent.parameters().side(), fill.quantity());
        calculator.calculateGrossPnlQuote(contract, signedQuantity, fill.price(), fill.price());

        Trade trade = Trade.builder()
                .tradeId(UUID.randomUUID())
                .symbol(intent.parameters().instrument())
                .type(intent.parameters().side() == ExecutionParameters.Side.BUY ? TradeType.BUY : TradeType.SELL)
                .entryPrice(fill.price())
                .currentPrice(fill.price())
                .quantity(signedQuantity)
                .openedAt(fill.executedAt())
                .tradeStatus(TradeStatus.OPEN)
                .build();
        trade.applyFinancialContract(contract);
        account.addTrade(trade);
        ensureCashBaseline(account);
        account.setRealizedBalance(account.getRealizedBalance().subtract(fill.fee()));
        account.setEquity(account.getRealizedBalance());
        account.setValuationStatus(AccountValuationStatus.UNAVAILABLE);
        account.setValuedAt(fill.executedAt());
        accounts.save(account);
        accounts.flush();
        record(trade.getTradeId(), intent, attempt, order, fill);
        return trade.getTradeId();
    }

    private UUID exit(Account account, ExecutionIntent intent, ExecutionAttempt attempt,
                      BrokerOrder order, BrokerOrder.Fill fill) {
        UUID targetId = intent.targetTradeId()
                .orElseThrow(() -> new IllegalStateException("CFD EXIT target is required"));
        Trade trade = account.getTrades().stream()
                .filter(candidate -> targetId.equals(candidate.getTradeId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("CFD EXIT target trade not found"));
        if (trade.getProductType() != InstrumentProductType.CFD
                || trade.getTradeStatus() != TradeStatus.OPEN || trade.getClosedAt() != null) {
            throw new IllegalStateException("CFD EXIT target trade is already closed or not a CFD");
        }
        if (fill.quantity().compareTo(trade.getQuantity().abs()) != 0) {
            throw new IllegalStateException("CFD EXIT fill quantity must equal target quantity");
        }
        if (!intent.parameters().instrument().equalsIgnoreCase(trade.getSymbol())) {
            throw new IllegalStateException("CFD EXIT instrument does not match target trade");
        }
        ExecutionParameters.Side expected = trade.getQuantity().signum() > 0
                ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY;
        if (intent.parameters().side() != expected) {
            throw new IllegalStateException("CFD EXIT side does not reduce target exposure");
        }

        CfdValuationService.CfdRealizationResult realized = valuation.realize(
                trade, ExecutionMode.PAPER, account.getBaseCurrency(), fill.price());
        if (!realized.available()) {
            throw new IllegalStateException(realized.reason());
        }
        Projection remaining = project(account, trade.getTradeId());
        ensureCashBaseline(account);
        BigDecimal realizedPnl = realized.grossPnlReporting().amount();
        account.setRealizedBalance(account.getRealizedBalance().add(realizedPnl).subtract(fill.fee()));
        trade.setExitPrice(fill.price());
        trade.setCurrentPrice(fill.price());
        trade.setPnl(realizedPnl);
        trade.setRealizedPnl(realizedPnl);
        trade.setClosedAt(fill.executedAt());
        trade.setTradeStatus(TradeStatus.CLOSED);
        account.setEquity(account.getRealizedBalance().add(remaining.unrealized()));
        account.setValuationStatus(remaining.status());
        account.setValuedAt(fill.executedAt());
        account.setMarketDataTimestamp(remaining.marketDataTimestamp());
        accounts.save(account);
        accounts.flush();
        record(trade.getTradeId(), intent, attempt, order, fill);
        return trade.getTradeId();
    }

    private Projection project(Account account, UUID excludedTradeId) {
        BigDecimal unrealized = BigDecimal.ZERO;
        Instant latestMark = null;
        for (Trade trade : account.getTrades()) {
            if (trade.getTradeStatus() != TradeStatus.OPEN
                    || trade.getTradeId().equals(excludedTradeId)) continue;
            CfdValuationService.CfdValuationResult result = valuation.value(
                    trade, ExecutionMode.PAPER, account.getBaseCurrency());
            if (result.status() != CfdValuationService.CfdValuationResult.Status.AVAILABLE) {
                throw new IllegalStateException(result.reason());
            }
            unrealized = unrealized.add(result.grossPnlReporting().amount());
            if (latestMark == null || result.markEffectiveAt().isAfter(latestMark)) {
                latestMark = result.markEffectiveAt();
            }
        }
        return new Projection(unrealized,
                latestMark == null ? AccountValuationStatus.FRESH : AccountValuationStatus.FRESH,
                latestMark);
    }

    private InstrumentFinancialContract resolveContract(String symbol) {
        List<MarketResponse> matches = Optional.ofNullable(marketData.findAll()).orElse(List.of()).stream()
                .filter(market -> market != null && symbol.equalsIgnoreCase(market.getSymbol())
                        && market.getProductType() == InstrumentProductType.CFD)
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("PAPER CFD market identity is unavailable or ambiguous");
        }
        MarketResponse market = matches.getFirst();
        MarketConstraints constraints = market.getMarketConstraints();
        if (market.getBaseAsset() == null || market.getQuoteAsset() == null
                || market.getQuantityUnit() == null || market.getContractMultiplier() == null
                || market.getContractSourceId() == null || market.getContractSourceVersion() == null
                || market.getContractObservedAt() == null || constraints == null
                || constraints.orderStepSize() == null || constraints.minimumOrderSize() == null
                || constraints.quantityPrecision() == null || constraints.pricePrecision() == null) {
            throw new IllegalStateException("PAPER CFD financial contract is incomplete");
        }
        QuantityUnit quantityUnit;
        try {
            quantityUnit = QuantityUnit.valueOf(market.getQuantityUnit());
        } catch (IllegalArgumentException invalidUnit) {
            throw new IllegalStateException("PAPER CFD quantity unit is invalid", invalidUnit);
        }
        return new InstrumentFinancialContract(symbol, InstrumentProductType.CFD,
                new CurrencyCode(market.getBaseAsset()), new CurrencyCode(market.getQuoteAsset()), quantityUnit,
                market.getContractMultiplier(), constraints.pricePrecision(), constraints.quantityPrecision(),
                constraints.minimumOrderSize(), constraints.orderStepSize(),
                new com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance(
                        market.getContractSourceId(), market.getContractSourceVersion(),
                        market.getContractObservedAt()));
    }

    private void ensureCashBaseline(Account account) {
        if (account.getRealizedBalance() == null) account.setRealizedBalance(account.getEquity());
    }

    private void requirePaperCashAccount(Account account) {
        if (account == null || account.getSettlementModel() != SettlementModel.CASH) {
            throw new IllegalStateException("PAPER CFD accounting requires a CASH account");
        }
    }

    private void record(UUID tradeId, ExecutionIntent intent, ExecutionAttempt attempt,
                        BrokerOrder order, BrokerOrder.Fill fill) {
        provenance.save(new TradeExecutionProvenanceStore.Provenance(UUID.randomUUID(), tradeId,
                attempt.id().value(), order.id().value(), fill.fillId(), fill.executedAt(),
                ExecutionMode.PAPER.name(), intent.purpose().name()));
    }

    private BrokerOrder.Fill fill(BrokerOrder order) {
        if (order.fills().isEmpty()) throw new IllegalStateException("Cannot settle PAPER CFD without fill");
        return order.fills().getLast();
    }

    private void validateFill(ExecutionIntent intent, BrokerOrder.Fill fill) {
        if (intent.parameters().quantity() == null
                || intent.parameters().quantity().compareTo(fill.quantity()) != 0) {
            throw new IllegalStateException("CFD fill quantity does not match execution intent");
        }
    }

    private BigDecimal signed(ExecutionParameters.Side side, BigDecimal quantity) {
        return side == ExecutionParameters.Side.BUY ? quantity : quantity.negate();
    }

    private record Projection(BigDecimal unrealized, AccountValuationStatus status,
                              Instant marketDataTimestamp) { }
}
