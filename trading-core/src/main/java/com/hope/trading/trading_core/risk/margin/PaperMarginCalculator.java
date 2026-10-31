package com.hope.trading.trading_core.risk.margin;

import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Exact V1 netting-only PAPER margin calculation. */
public final class PaperMarginCalculator {
    public Result calculate(MarginPolicy policy, Request request) {
        if (policy == null || request == null || !policy.applicableAt(request.requestedAt())
                || !policy.instrument().equalsIgnoreCase(request.instrument())
                || request.existingSignedQuantity() == null || request.proposedSignedQuantity() == null
                || request.referencePrice() == null || request.contractMultiplier() == null
                || request.referencePrice().signum() <= 0 || request.contractMultiplier().signum() <= 0
                || request.reportingCurrency() == null || request.quoteCurrency() == null
                || request.quoteToReportingRate() == null || request.quoteToReportingRate().signum() <= 0) return Result.unavailable("MISSING_POLICY_OR_FACT");

        BigDecimal before = margin(request.existingSignedQuantity(), request.referencePrice(),
                request.contractMultiplier(), policy.marginRate());
        BigDecimal after = margin(request.existingSignedQuantity().add(request.proposedSignedQuantity()),
                request.referencePrice(), request.contractMultiplier(), policy.marginRate());
        BigDecimal quoteAmount = after.subtract(before).max(BigDecimal.ZERO);
        if (quoteAmount.signum() == 0) return Result.available(BigDecimal.ZERO.setScale(policy.reportingCurrencyScale(), RoundingMode.UNNECESSARY),
                request.reportingCurrency(), policy.version());
        boolean identityConversion = request.quoteCurrency().equalsIgnoreCase(request.reportingCurrency());
        List<MarketValuationPort.ConversionLeg> legs = identityConversion || request.fx() == null
                ? List.of() : request.fx();
        if (!identityConversion && legs.isEmpty())
            return Result.unavailable("MISSING_OR_AMBIGUOUS_FX");
        if (request.maxObservationAge() == null || request.maxObservationAge().isNegative())
            return Result.unavailable("INVALID_FX_FRESHNESS");
        BigDecimal rate = BigDecimal.ONE;
        Instant oldest = request.requestedAt();
        String from = request.quoteCurrency();
        Set<String> visited = new HashSet<>();
        visited.add(from.toUpperCase(Locale.ROOT));
        for (MarketValuationPort.ConversionLeg leg : legs) {
            if (leg == null || leg.rate() == null || leg.rate().signum() <= 0
                    || leg.effectiveAt() == null || leg.capturedAt() == null
                    || leg.effectiveAt().isAfter(request.requestedAt()) || leg.capturedAt().isAfter(request.requestedAt())
                    || blank(leg.provenance()) || blank(leg.fromCurrency()) || blank(leg.toCurrency())
                    || !leg.fromCurrency().equalsIgnoreCase(from))
                return Result.unavailable("INVALID_FX");
            rate = rate.multiply(leg.rate());
            from = leg.toCurrency();
            if (!visited.add(from.toUpperCase(Locale.ROOT))) return Result.unavailable("INVALID_FX");
            if (leg.effectiveAt().isBefore(oldest)) oldest = leg.effectiveAt();
            if (leg.capturedAt().isBefore(oldest)) oldest = leg.capturedAt();
        }
        if (!request.quoteCurrency().equalsIgnoreCase(request.reportingCurrency())
                && (!from.equalsIgnoreCase(request.reportingCurrency())
                || oldest.isBefore(request.requestedAt().minus(request.maxObservationAge()))))
            return Result.unavailable("INVALID_FX");
        if (rate.compareTo(request.quoteToReportingRate()) != 0) return Result.unavailable("FX_RATE_MISMATCH");
        BigDecimal reportingAmount = quoteAmount.multiply(rate)
                .setScale(policy.reportingCurrencyScale(), RoundingMode.CEILING);
        return Result.available(reportingAmount, request.reportingCurrency(), policy.version());
    }

    private static BigDecimal margin(BigDecimal quantity, BigDecimal price, BigDecimal multiplier, BigDecimal rate) {
        return quantity.abs().multiply(price).multiply(multiplier).multiply(rate);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record Request(String instrument, BigDecimal existingSignedQuantity, BigDecimal proposedSignedQuantity,
                          BigDecimal referencePrice, BigDecimal contractMultiplier, String quoteCurrency,
                           String reportingCurrency, List<MarketValuationPort.ConversionLeg> fx, Instant requestedAt,
                            Duration maxObservationAge, BigDecimal quoteToReportingRate) {
        public Request(String instrument, BigDecimal existingSignedQuantity, BigDecimal proposedSignedQuantity,
                       BigDecimal referencePrice, BigDecimal contractMultiplier, String quoteCurrency,
                       String reportingCurrency, List<MarketValuationPort.ConversionLeg> fx, Instant requestedAt) {
            this(instrument, existingSignedQuantity, proposedSignedQuantity, referencePrice, contractMultiplier,
                    quoteCurrency, reportingCurrency, fx, requestedAt, Duration.ofMinutes(5), BigDecimal.ONE);
        }
    }
    public record Result(boolean available, BigDecimal amount, String currency, String policyVersion, String reason) {
        static Result available(BigDecimal amount, String currency, String version) {
            return new Result(true, amount, currency.toUpperCase(Locale.ROOT), version, null);
        }
        static Result unavailable(String reason) { return new Result(false, null, null, null, reason); }
    }
}
