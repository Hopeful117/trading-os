package com.hope.trading.trading_core.shared.domain.financial;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure deterministic calculations over a validated provider-neutral CFD contract.
 *
 * Monetary results retain exact BigDecimal arithmetic and are never rounded here.
 * Scale and reporting presentation belong to a later explicit boundary; applying
 * a MathContext or a currency-specific scale in this class would lose information.
 * A stop on or beyond the profitable side is rejected because it is not a valid
 * adverse protection configuration, rather than being represented as zero risk.
 */
public final class CfdFinancialCalculator {
    public Money calculateNotionalQuote(InstrumentFinancialContract contract,
                                        BigDecimal signedQuantity,
                                        BigDecimal referencePrice) {
        requireCfdContract(contract);
        requireQuantity(contract, signedQuantity);
        requirePrice(contract, referencePrice, "reference price");
        return quoteMoney(signedQuantity.abs().multiply(referencePrice)
                .multiply(contract.contractMultiplier()), contract);
    }

    public Money calculateNotionalReporting(InstrumentFinancialContract contract,
                                            BigDecimal signedQuantity,
                                            BigDecimal referencePrice,
                                            CurrencyCode reportingCurrency,
                                            CurrencyConversionFact conversionFact) {
        return convert(calculateNotionalQuote(contract, signedQuantity, referencePrice),
                reportingCurrency, conversionFact);
    }

    public Money calculateGrossPnlQuote(InstrumentFinancialContract contract,
                                        BigDecimal signedQuantity,
                                        BigDecimal entryPrice,
                                        BigDecimal markOrExitPrice) {
        requireCfdContract(contract);
        requireQuantity(contract, signedQuantity);
        requirePrice(contract, entryPrice, "entry price");
        requirePrice(contract, markOrExitPrice, "mark or exit price");
        return quoteMoney(signedQuantity.multiply(markOrExitPrice.subtract(entryPrice))
                .multiply(contract.contractMultiplier()), contract);
    }

    public Money calculateGrossPnlReporting(InstrumentFinancialContract contract,
                                            BigDecimal signedQuantity,
                                            BigDecimal entryPrice,
                                            BigDecimal markOrExitPrice,
                                            CurrencyCode reportingCurrency,
                                            CurrencyConversionFact conversionFact) {
        return convert(calculateGrossPnlQuote(contract, signedQuantity, entryPrice, markOrExitPrice),
                reportingCurrency, conversionFact);
    }

    public Money calculateStopLossRiskQuote(InstrumentFinancialContract contract,
                                            BigDecimal signedQuantity,
                                            BigDecimal entryPrice,
                                            BigDecimal stopPrice) {
        requireCfdContract(contract);
        requireQuantity(contract, signedQuantity);
        requirePrice(contract, entryPrice, "entry price");
        requirePrice(contract, stopPrice, "stop price");
        BigDecimal adverseDistance;
        if (signedQuantity.signum() > 0) {
            if (stopPrice.compareTo(entryPrice) >= 0) {
                throw new IllegalArgumentException("long stop price must be below entry price");
            }
            adverseDistance = entryPrice.subtract(stopPrice);
        } else {
            if (stopPrice.compareTo(entryPrice) <= 0) {
                throw new IllegalArgumentException("short stop price must be above entry price");
            }
            adverseDistance = stopPrice.subtract(entryPrice);
        }
        return quoteMoney(adverseDistance.multiply(signedQuantity.abs())
                .multiply(contract.contractMultiplier()), contract);
    }

    public Money calculateStopLossRiskReporting(InstrumentFinancialContract contract,
                                                BigDecimal signedQuantity,
                                                BigDecimal entryPrice,
                                                BigDecimal stopPrice,
                                                CurrencyCode reportingCurrency,
                                                CurrencyConversionFact conversionFact) {
        return convert(calculateStopLossRiskQuote(contract, signedQuantity, entryPrice, stopPrice),
                reportingCurrency, conversionFact);
    }

    public Money convert(Money amount, CurrencyCode targetCurrency,
                         CurrencyConversionFact conversionFact) {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(targetCurrency, "target currency is required");
        CurrencyCode sourceCurrency = new CurrencyCode(amount.currency());
        if (sourceCurrency.equals(targetCurrency)) {
            return new Money(amount.amount(), targetCurrency.value());
        }
        if (conversionFact == null) {
            throw new IllegalArgumentException("conversion fact is required for cross-currency conversion");
        }
        if (!conversionFact.sourceCurrency().equals(sourceCurrency)
                || !conversionFact.targetCurrency().equals(targetCurrency)) {
            throw new IllegalArgumentException("conversion fact direction does not match requested conversion");
        }
        return new Money(amount.amount().multiply(conversionFact.rate()), targetCurrency.value());
    }

    private static void requireCfdContract(InstrumentFinancialContract contract) {
        Objects.requireNonNull(contract, "financial contract is required");
        if (contract.productType() != InstrumentProductType.CFD) {
            throw new IllegalArgumentException("instrument product type is not compatible with CFD calculations");
        }
        if (contract.quantityUnit() != QuantityUnit.UNDERLYING_UNITS) {
            throw new IllegalArgumentException("quantity unit is not supported by CFD calculations");
        }
    }

    private static void requireQuantity(InstrumentFinancialContract contract, BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("quantity must be non-zero");
        }
        BigDecimal absoluteQuantity = quantity.abs();
        if (decimalPlaces(absoluteQuantity) > contract.quantityPrecision()) {
            throw new IllegalArgumentException("quantity exceeds contract precision");
        }
        if (absoluteQuantity.compareTo(contract.minimumQuantity()) < 0
                || absoluteQuantity.remainder(contract.quantityIncrement()).signum() != 0) {
            throw new IllegalArgumentException("quantity does not satisfy contract constraints");
        }
    }

    private static void requirePrice(InstrumentFinancialContract contract,
                                     BigDecimal price,
                                     String name) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        if (decimalPlaces(price) > contract.pricePrecision()) {
            throw new IllegalArgumentException(name + " exceeds contract precision");
        }
    }

    private static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static Money quoteMoney(BigDecimal amount, InstrumentFinancialContract contract) {
        return new Money(amount, contract.quoteCurrency().value());
    }
}
