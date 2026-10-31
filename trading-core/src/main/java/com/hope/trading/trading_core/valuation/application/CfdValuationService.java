package com.hope.trading.trading_core.valuation.application;

import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.market_data.valuation.ReadOnlyMarketValuationPort;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.shared.domain.financial.CfdFinancialCalculator;
import com.hope.trading.trading_core.shared.domain.financial.CurrencyConversionFact;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import com.hope.trading.risk.domain.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
public class CfdValuationService {
    private final ReadOnlyMarketValuationPort marketValuation;
    private final CfdFinancialCalculator calculator;
    private final Clock clock;

    public CfdValuationService(ReadOnlyMarketValuationPort marketValuation, Clock clock) {
        this.marketValuation = marketValuation;
        this.calculator = new CfdFinancialCalculator();
        this.clock = clock;
    }

    public CfdValuationResult value(Trade trade, ExecutionMode executionMode) {
        if (trade == null || trade.getAccount() == null) {
            return unavailable("CFD account context is unavailable");
        }
        return value(trade, executionMode, trade.getAccount().getBaseCurrency());
    }

    public CfdValuationResult value(Trade trade, ExecutionMode executionMode, String reportingCurrency) {
        if (executionMode != ExecutionMode.PAPER) {
            return unavailable("LIVE CFD provider-authoritative exposure is unavailable");
        }
        try {
            if (trade == null || trade.getAccount() == null
                    || trade.getAccount().getBaseCurrency() == null
                    || reportingCurrency == null
                    || !trade.getAccount().getBaseCurrency().equalsIgnoreCase(reportingCurrency)) {
                return unavailable("CFD reporting currency does not match the account");
            }
            InstrumentFinancialContract contract = contract(trade);
            if (trade.getQuantity() == null || trade.getQuantity().signum() == 0
                    || reportingCurrency == null || reportingCurrency.isBlank()) {
                return unavailable("CFD exposure or reporting currency is unavailable");
            }
            MarketValuationPort.PriceUse priceUse = trade.getQuantity().signum() > 0
                    ? MarketValuationPort.PriceUse.CONSERVATIVE_SELL
                    : MarketValuationPort.PriceUse.CONSERVATIVE_BUY;
            MarketValuationPort.Snapshot snapshot = marketValuation.valueReadOnly(
                    reportingCurrency,
                    clock.instant(),
                    List.of(new MarketValuationPort.Instrument(
                            trade.getTradeId().toString(), trade.getSymbol(), priceUse)),
                    List.of(),
                    MarketValuationPort.FreshnessRequirement.t0());
            if (snapshot.reportingCurrency() == null
                    || !snapshot.reportingCurrency().equalsIgnoreCase(reportingCurrency)) {
                return unavailable("CFD valuation reporting currency is inconsistent");
            }
            MarketValuationPort.Fact fact = snapshot.facts().stream()
                    .filter(candidate -> candidate.id().equals(trade.getTradeId().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CFD mark fact is unavailable"));
            validateFact(snapshot, fact, priceUse);

            Money quotePnl = calculator.calculateGrossPnlQuote(contract, trade.getQuantity(),
                    trade.getEntryPrice(), fact.sourcePrice());
            Money reportingPnl = quotePnl;
            CurrencyCode target = new CurrencyCode(reportingCurrency);
            if (!contract.quoteCurrency().equals(target)) {
                List<MarketValuationPort.ConversionLeg> legs = validateConversion(
                        contract.quoteCurrency().value(), target.value(), fact);
                BigDecimal rate = legs.stream().map(MarketValuationPort.ConversionLeg::rate)
                        .reduce(BigDecimal.ONE, BigDecimal::multiply);
                if (fact.quoteToReportingRate() == null
                        || rate.compareTo(fact.quoteToReportingRate()) != 0) {
                    return unavailable("CFD currency conversion fact is unavailable");
                }
                Instant conversionAt = legs.stream().map(MarketValuationPort.ConversionLeg::effectiveAt)
                        .max(Instant::compareTo).orElseThrow();
                CurrencyConversionFact conversion = new CurrencyConversionFact(contract.quoteCurrency(), target,
                        rate, provenance(snapshot, conversionAt));
                reportingPnl = calculator.convert(quotePnl, target, conversion);
            }
            return CfdValuationResult.available(trade.getTradeId(), trade.getSymbol(), trade.getQuantity(),
                    trade.getEntryPrice(), fact.sourcePrice(), priceUse, quotePnl, reportingPnl,
                    fact.sourceEffectiveAt(), fact.sourceCapturedAt(), fact.conversionEffectiveAt(),
                    fact.conversionCapturedAt(), fact.sourceProvenance(), fact.conversionLegs());
        } catch (RuntimeException failure) {
            return unavailable(failure.getMessage() == null ? "CFD valuation facts are unavailable" : failure.getMessage());
        }
    }

    /**
     * Values a persisted CFD at an explicit local exit price. The price is the
     * execution fill, so only the read-only currency fact is acquired here.
     */
    public CfdRealizationResult realize(Trade trade, ExecutionMode executionMode,
                                        String reportingCurrency, BigDecimal exitPrice) {
        if (executionMode != ExecutionMode.PAPER) {
            return CfdRealizationResult.unavailable("LIVE CFD provider-authoritative exposure is unavailable");
        }
        try {
            if (trade == null || trade.getAccount() == null || reportingCurrency == null
                    || !reportingCurrency.equalsIgnoreCase(trade.getAccount().getBaseCurrency())) {
                return CfdRealizationResult.unavailable("CFD reporting currency does not match the account");
            }
            InstrumentFinancialContract contract = contract(trade);
            if (exitPrice == null || exitPrice.signum() <= 0) {
                return CfdRealizationResult.unavailable("CFD exit price is unavailable");
            }
            Money quotePnl = calculator.calculateGrossPnlQuote(contract, trade.getQuantity(),
                    trade.getEntryPrice(), exitPrice);
            Money reportingPnl = quotePnl;
            Instant conversionEffectiveAt = null;
            Instant conversionCapturedAt = null;
            List<MarketValuationPort.ConversionLeg> conversionLegs = List.of();
            CurrencyCode target = new CurrencyCode(reportingCurrency);
            if (!contract.quoteCurrency().equals(target)) {
                MarketValuationPort.Snapshot snapshot = marketValuation.valueReadOnly(
                        reportingCurrency, clock.instant(), List.of(),
                        List.of(new MarketValuationPort.Asset(trade.getTradeId().toString(),
                                contract.quoteCurrency().value())), MarketValuationPort.FreshnessRequirement.t1());
                if (!snapshot.complete()) {
                    return CfdRealizationResult.unavailable("CFD currency conversion fact is unavailable");
                }
                MarketValuationPort.Fact fact = snapshot.facts().stream()
                        .filter(candidate -> candidate.id().equals(trade.getTradeId().toString()))
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "CFD currency conversion fact is unavailable"));
                if (!reportingCurrency.equalsIgnoreCase(snapshot.reportingCurrency())
                        || !"AVAILABLE".equals(fact.status())) {
                    return CfdRealizationResult.unavailable("CFD currency conversion fact is unavailable");
                }
                conversionLegs = validateConversion(contract.quoteCurrency().value(), target.value(), fact);
                BigDecimal rate = conversionLegs.stream().map(MarketValuationPort.ConversionLeg::rate)
                        .reduce(BigDecimal.ONE, BigDecimal::multiply);
                if (fact.quoteToReportingRate() == null
                        || rate.compareTo(fact.quoteToReportingRate()) != 0) {
                    return CfdRealizationResult.unavailable("CFD currency conversion fact is unavailable");
                }
                conversionEffectiveAt = conversionLegs.stream().map(MarketValuationPort.ConversionLeg::effectiveAt)
                        .max(Instant::compareTo).orElseThrow();
                conversionCapturedAt = conversionLegs.stream().map(MarketValuationPort.ConversionLeg::capturedAt)
                        .max(Instant::compareTo).orElseThrow();
                reportingPnl = calculator.convert(quotePnl, target,
                        new CurrencyConversionFact(contract.quoteCurrency(), target, rate,
                                provenance(snapshot, conversionEffectiveAt)));
            }
            return CfdRealizationResult.available(quotePnl, reportingPnl, conversionEffectiveAt,
                    conversionCapturedAt, conversionLegs);
        } catch (RuntimeException failure) {
            return CfdRealizationResult.unavailable(failure.getMessage() == null
                    ? "CFD realization facts are unavailable" : failure.getMessage());
        }
    }

    private InstrumentFinancialContract contract(Trade trade) {
        if (trade == null || trade.getProductType() != InstrumentProductType.CFD
                || trade.getBaseCurrency() == null || trade.getQuoteCurrency() == null
                || trade.getQuantityUnit() != QuantityUnit.UNDERLYING_UNITS
                || trade.getContractMultiplier() == null || trade.getPricePrecision() == null
                || trade.getQuantityPrecision() == null || trade.getMinimumQuantity() == null
                || trade.getQuantityIncrement() == null || trade.getContractSourceId() == null
                || trade.getContractSourceVersion() == null || trade.getContractObservedAt() == null) {
            throw new IllegalArgumentException("persisted CFD financial contract is incomplete");
        }
        return new InstrumentFinancialContract(trade.getSymbol(), trade.getProductType(),
                new CurrencyCode(trade.getBaseCurrency()), new CurrencyCode(trade.getQuoteCurrency()),
                trade.getQuantityUnit(), trade.getContractMultiplier(), trade.getPricePrecision(),
                trade.getQuantityPrecision(), trade.getMinimumQuantity(), trade.getQuantityIncrement(),
                new FinancialContractProvenance(trade.getContractSourceId(), trade.getContractSourceVersion(),
                        trade.getContractObservedAt()));
    }

    private void validateFact(MarketValuationPort.Snapshot snapshot, MarketValuationPort.Fact fact,
                              MarketValuationPort.PriceUse expectedPriceUse) {
        if (!snapshot.complete() || !"AVAILABLE".equals(fact.status())
                || fact.priceUse() != expectedPriceUse || fact.sourcePrice() == null
                || fact.sourceEffectiveAt() == null || fact.sourceCapturedAt() == null
                || fact.sourceProvenance() == null || fact.sourceProvenance().isBlank()) {
            throw new IllegalStateException("CFD mark fact is incomplete or stale");
        }
    }

    private List<MarketValuationPort.ConversionLeg> validateConversion(
            String sourceCurrency, String targetCurrency, MarketValuationPort.Fact fact) {
        List<MarketValuationPort.ConversionLeg> legs = fact.conversionLegs();
        if (legs == null || legs.isEmpty()) {
            throw new IllegalStateException("CFD currency conversion legs are unavailable");
        }
        String expectedFrom = sourceCurrency;
        for (MarketValuationPort.ConversionLeg leg : legs) {
            if (leg == null || leg.fromCurrency() == null || leg.toCurrency() == null) {
                throw new IllegalStateException("CFD currency conversion leg is invalid");
            }
            if (!leg.fromCurrency().equalsIgnoreCase(expectedFrom)) {
                throw new IllegalStateException("CFD currency conversion direction is invalid");
            }
            if (leg.rate() == null || leg.rate().signum() <= 0
                    || leg.provenance() == null || leg.provenance().isBlank()
                    || leg.effectiveAt() == null || leg.capturedAt() == null) {
                throw new IllegalStateException("CFD currency conversion leg is invalid");
            }
            expectedFrom = leg.toCurrency();
        }
        if (!Objects.equals(expectedFrom.toUpperCase(), targetCurrency.toUpperCase())) {
            throw new IllegalStateException("CFD currency conversion direction is invalid");
        }
        return legs;
    }

    private FinancialContractProvenance provenance(MarketValuationPort.Snapshot snapshot, Instant observedAt) {
        return new FinancialContractProvenance("market-valuation:" + snapshot.policyVersion(),
                Math.max(1, snapshot.sourceVersion()), observedAt);
    }

    private CfdValuationResult unavailable(String reason) {
        return CfdValuationResult.unavailable(reason);
    }

    public record CfdValuationResult(Status status, String reason, java.util.UUID tradeId,
                                     String symbol, BigDecimal signedQuantity, BigDecimal entryPrice,
                                     BigDecimal markPrice, MarketValuationPort.PriceUse priceUse,
                                     Money grossPnlQuote, Money grossPnlReporting,
                                     Instant markEffectiveAt, Instant markCapturedAt,
                                     Instant conversionEffectiveAt, Instant conversionCapturedAt,
                                     String provenance, List<MarketValuationPort.ConversionLeg> conversionLegs) {
        public static CfdValuationResult available(java.util.UUID tradeId, String symbol,
                                                   BigDecimal signedQuantity, BigDecimal entryPrice,
                                                   BigDecimal markPrice, MarketValuationPort.PriceUse priceUse,
                                                   Money grossPnlQuote, Money grossPnlReporting,
                                                   Instant markEffectiveAt, Instant markCapturedAt,
                                                   Instant conversionEffectiveAt, Instant conversionCapturedAt,
                                                   String provenance,
                                                   List<MarketValuationPort.ConversionLeg> conversionLegs) {
            return new CfdValuationResult(Status.AVAILABLE, null, tradeId, symbol, signedQuantity,
                    entryPrice, markPrice, priceUse, grossPnlQuote, grossPnlReporting,
                    markEffectiveAt, markCapturedAt, conversionEffectiveAt, conversionCapturedAt,
                    provenance, List.copyOf(conversionLegs));
        }

        public static CfdValuationResult unavailable(String reason) {
            return new CfdValuationResult(Status.UNAVAILABLE, reason, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, List.of());
        }

        public enum Status { AVAILABLE, UNAVAILABLE }
    }

    public record CfdRealizationResult(CfdValuationResult.Status status, String reason, Money grossPnlQuote,
                                       Money grossPnlReporting, Instant conversionEffectiveAt,
                                       Instant conversionCapturedAt,
                                       List<MarketValuationPort.ConversionLeg> conversionLegs) {
        public static CfdRealizationResult available(Money grossPnlQuote, Money grossPnlReporting,
                                                     Instant conversionEffectiveAt,
                                                     Instant conversionCapturedAt,
                                                     List<MarketValuationPort.ConversionLeg> conversionLegs) {
            return new CfdRealizationResult(CfdValuationResult.Status.AVAILABLE, null, grossPnlQuote, grossPnlReporting,
                    conversionEffectiveAt, conversionCapturedAt, List.copyOf(conversionLegs));
        }

        public static CfdRealizationResult unavailable(String reason) {
            return new CfdRealizationResult(CfdValuationResult.Status.UNAVAILABLE, reason, null, null,
                    null, null, List.of());
        }

        public boolean available() {
            return status == CfdValuationResult.Status.AVAILABLE;
        }
    }
}
