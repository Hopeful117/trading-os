package com.hope.trading.trading_core.risk.application;

import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
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
import java.util.List;
import java.util.Objects;

/** Projects validated CFD facts into the generic, currency-normalized Risk boundary. */
@Service
public final class CfdRiskProjectionService {
    private final CfdFinancialCalculator calculator = new CfdFinancialCalculator();

    public Proposed projectProposed(InstrumentFinancialContract contract, BigDecimal signedQuantity,
                                    BigDecimal entryPrice, BigDecimal stopPrice,
                                    BigDecimal fallbackExpectedRisk, MarketValuationPort.Fact marketFact,
                                    long marketSourceVersion, String reportingCurrency) {
        validateMarketFact(marketFact);
        if (stopPrice == null || stopPrice.signum() <= 0) {
            throw new IllegalArgumentException("CFD protective stop is unavailable");
        }
        BigDecimal referencePrice = entryPrice == null ? marketFact.sourcePrice() : entryPrice;
        Money notional = reporting(contract, calculator.calculateNotionalQuote(contract, signedQuantity,
                        referencePrice), marketFact, marketSourceVersion, reportingCurrency);
        Money stopRisk = reporting(contract, calculator.calculateStopLossRiskQuote(contract, signedQuantity,
                referencePrice, stopPrice), marketFact, marketSourceVersion, reportingCurrency);
        return new Proposed(notional, stopRisk);
    }

    public Position projectPosition(InstrumentFinancialContract contract, BigDecimal signedQuantity,
                                    BigDecimal entryPrice, List<RiskFactsProvider.Stop> stops,
                                    MarketValuationPort.Fact marketFact, long marketSourceVersion,
                                    String reportingCurrency) {
        validateMarketFact(marketFact);
        Money marketValue = reporting(contract, calculator.calculateNotionalQuote(contract, signedQuantity,
                marketFact.sourcePrice()), marketFact, marketSourceVersion, reportingCurrency);
        BigDecimal loss = BigDecimal.ZERO;
        for (RiskFactsProvider.Stop stop : stops == null ? List.<RiskFactsProvider.Stop>of() : stops) {
            if (stop == null || stop.quantity() == null || stop.quantity().signum() <= 0
                    || stop.stopPrice() == null) {
                throw new IllegalArgumentException("CFD protective stop fact is unavailable");
            }
            boolean adverse = signedQuantity.signum() > 0
                    ? stop.stopPrice().compareTo(entryPrice) < 0
                    : stop.stopPrice().compareTo(entryPrice) > 0;
            if (!adverse) continue;
            BigDecimal signedStopQuantity = signedQuantity.signum() > 0
                    ? stop.quantity() : stop.quantity().negate();
            loss = loss.add(reporting(contract, calculator.calculateStopLossRiskQuote(contract,
                    signedStopQuantity, entryPrice, stop.stopPrice()), marketFact, marketSourceVersion,
                    reportingCurrency).amount());
        }
        return new Position(marketValue, new Money(loss, reportingCurrency));
    }

    public InstrumentFinancialContract fromTrade(Trade trade) {
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

    private Money reporting(InstrumentFinancialContract contract, Money quoteAmount,
                            MarketValuationPort.Fact marketFact, long marketSourceVersion,
                            String reportingCurrency) {
        CurrencyCode target = new CurrencyCode(reportingCurrency);
        if (contract.quoteCurrency().equals(target)) {
            return new Money(quoteAmount.amount(), target.value());
        }
        List<MarketValuationPort.ConversionLeg> legs = marketFact.conversionLegs();
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("CFD currency conversion facts are unavailable");
        }
        String expectedFrom = contract.quoteCurrency().value();
        BigDecimal rate = BigDecimal.ONE;
        Instant observedAt = null;
        for (MarketValuationPort.ConversionLeg leg : legs) {
            if (leg == null || leg.fromCurrency() == null || leg.toCurrency() == null
                    || !leg.fromCurrency().equalsIgnoreCase(expectedFrom)
                    || leg.rate() == null || leg.rate().signum() <= 0
                    || blank(leg.provenance()) || leg.effectiveAt() == null || leg.capturedAt() == null) {
                throw new IllegalArgumentException("CFD currency conversion facts are invalid");
            }
            expectedFrom = leg.toCurrency();
            rate = rate.multiply(leg.rate());
            observedAt = observedAt == null || leg.effectiveAt().isAfter(observedAt)
                    ? leg.effectiveAt() : observedAt;
        }
        if (!expectedFrom.equalsIgnoreCase(target.value()) || marketFact.quoteToReportingRate() == null
                || rate.compareTo(marketFact.quoteToReportingRate()) != 0) {
            throw new IllegalArgumentException("CFD currency conversion direction is invalid");
        }
        CurrencyConversionFact conversion = new CurrencyConversionFact(contract.quoteCurrency(), target,
                rate, new FinancialContractProvenance("market-valuation:" + marketFact.sourceProvenance(),
                Math.max(1, marketSourceVersion), observedAt));
        return calculator.convert(quoteAmount, target, conversion);
    }

    private void validateMarketFact(MarketValuationPort.Fact fact) {
        if (fact == null || !"AVAILABLE".equals(fact.status()) || fact.sourcePrice() == null
                || fact.sourcePrice().signum() <= 0 || fact.value() == null || fact.value().signum() <= 0
                || blank(fact.sourceProvenance()) || fact.sourceEffectiveAt() == null
                || fact.sourceCapturedAt() == null) {
            throw new IllegalArgumentException("CFD market fact is incomplete or stale");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Proposed(Money notional, Money stopRisk) { }
    public record Position(Money marketValue, Money lossAtStop) { }
}
