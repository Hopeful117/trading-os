package com.hope.trading.trading_core.risk.margin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Durable, provider-neutral context captured at T0 and reused unchanged at T1. */
public record MarginResolutionContext(UUID financialAccountId, UUID brokerAccountId,
                                      UUID policyId, String policyVersion,
                                      UUID exposureSnapshotId, String contractId, Long contractVersion,
                                      UUID valuationSnapshotId, long valuationSourceVersion,
                                      String quoteCurrency, String reportingCurrency,
                                      BigDecimal quoteToReportingRate, Duration maxObservationAge,
                                      int reportingCurrencyScale, Instant requestedAt) {
    public MarginResolutionContext {
        if (financialAccountId == null || brokerAccountId == null || policyId == null
                || policyVersion == null || policyVersion.isBlank() || exposureSnapshotId == null
                || contractId == null || contractId.isBlank() || contractVersion == null || contractVersion < 1
                || valuationSnapshotId == null || valuationSourceVersion < 1 || quoteCurrency == null
                || reportingCurrency == null || quoteToReportingRate == null || quoteToReportingRate.signum() <= 0
                || maxObservationAge == null || maxObservationAge.isNegative() || reportingCurrencyScale < 0
                || requestedAt == null) throw new IllegalArgumentException("Incomplete margin resolution context");
    }
}
