package com.hope.trading.trading_core.risk.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.hope.trading.trading_core.risk.margin.MarginResolutionContext;

/** Read boundary for an authoritative broker/account proposed-order margin fact. */
public interface RequiredMarginPort {
    Optional<Fact> resolve(Request request);

    record Request(UUID brokerAccountId, String instrument, String direction,
                   BigDecimal quantity, BigDecimal price, Instant requestedAt,
                   UUID financialAccountId, String reportingCurrency, String policyId,
                   String policyVersion, String exposureSnapshotId, MarginResolutionContext context) {
        public Request(UUID brokerAccountId, String instrument, String direction,
                       BigDecimal quantity, BigDecimal price, Instant requestedAt) {
            this(brokerAccountId, instrument, direction, quantity, price, requestedAt,
                    null, null, null, null, null, null);
        }
        public Request(UUID brokerAccountId, String instrument, String direction,
                       BigDecimal quantity, BigDecimal price, Instant requestedAt,
                       UUID financialAccountId, String reportingCurrency, String policyId,
                       String policyVersion, String exposureSnapshotId) {
            this(brokerAccountId, instrument, direction, quantity, price, requestedAt,
                    financialAccountId, reportingCurrency, policyId, policyVersion, exposureSnapshotId, null);
        }

        public Request withContext(MarginResolutionContext value) {
            return new Request(brokerAccountId, instrument, direction, quantity, price, requestedAt,
                    financialAccountId, reportingCurrency, value.policyId().toString(), value.policyVersion(),
                    value.exposureSnapshotId().toString(), value);
        }
    }
    record Fact(BigDecimal amount, String currency, String sourceId,
                long sourceVersion, Instant observedAt,
                MarginResolutionContext context, MarginEvidence evidence) {
        public Fact(BigDecimal amount, String currency, String sourceId, long sourceVersion, Instant observedAt) {
            this(amount, currency, sourceId, sourceVersion, observedAt, null, null);
        }
    }
    record MarginEvidence(String reason, String policySourceCategory, String policySourceReference,
                          String contractId, Long contractVersion, UUID exposureSnapshotId,
                          UUID valuationSnapshotId, long valuationSourceVersion,
                          BigDecimal quoteToReportingRate, String maxObservationAge,
                          int reportingCurrencyScale) { }

    record Resolution(Fact fact, String reason) {
        public boolean available() { return fact != null; }
        public static Resolution available(Fact fact) { return new Resolution(fact, null); }
        public static Resolution unavailable(String reason) { return new Resolution(null, reason); }
    }

    default Resolution resolveDetailed(Request request) {
        return resolve(request).map(Resolution::available).orElseGet(() -> Resolution.unavailable("REQUIRED_MARGIN_UNAVAILABLE"));
    }

    default Optional<MarginResolutionContext> resolveContext(Request request) {
        return Optional.ofNullable(request == null ? null : request.context());
    }
}
