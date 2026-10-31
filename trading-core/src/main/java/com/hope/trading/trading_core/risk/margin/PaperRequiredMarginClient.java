package com.hope.trading.trading_core.risk.margin;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Local PAPER authority. It deliberately has no broker margin dependency. */
@Component
public final class PaperRequiredMarginClient {
    private final AccountRepository accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final TradeRepository trades;
    private final MarginPolicyPersistence policies;
    private final InstrumentFinancialContractResolver contracts;
    private final MarketValuationPort market;
    private final Clock clock;
    private final PaperMarginExposureSnapshot exposures = new PaperMarginExposureSnapshot();
    private final PaperMarginCalculator calculator = new PaperMarginCalculator();

    public PaperRequiredMarginClient(AccountRepository accounts, BrokerAccountRepository brokerAccounts,
                                     TradeRepository trades, MarginPolicyPersistence policies,
                                     InstrumentFinancialContractResolver contracts, MarketValuationPort market,
                                     Clock clock) {
        this.accounts = accounts; this.brokerAccounts = brokerAccounts; this.trades = trades;
        this.policies = policies; this.contracts = contracts; this.market = market; this.clock = clock;
    }

    public Optional<RequiredMarginPort.Fact> resolve(RequiredMarginPort.Request request) {
        return Optional.ofNullable(resolveDetailed(request).fact());
    }

    public RequiredMarginPort.Resolution resolveDetailed(RequiredMarginPort.Request request) {
        if (request == null || request.financialAccountId() == null || request.brokerAccountId() == null
                || request.quantity() == null || request.quantity().signum() <= 0 || request.price() == null
                || request.price().signum() <= 0 || request.requestedAt() == null) return unavailable("INVALID_REQUEST");
        try {
            var account = accounts.findById(request.financialAccountId()).orElse(null);
            var broker = brokerAccounts.findById(request.brokerAccountId()).orElse(null);
            if (account == null || broker == null || broker.executionMode() != ExecutionMode.PAPER
                    || !request.brokerAccountId().equals(account.getBrokerAccountId())
                    || account.getBaseCurrency() == null || !account.getBrokerAccountId().equals(broker.id())
                    || account.getUser() == null || !account.getUser().getUserId().equals(broker.ownerId())) return unavailable("BROKER_ACCOUNT_MAPPING_INVALID");
            if (request.reportingCurrency() != null
                    && !account.getBaseCurrency().equalsIgnoreCase(request.reportingCurrency())) return unavailable("REPORTING_CURRENCY_MISMATCH");
            if (request.context() != null
                    && (!request.context().financialAccountId().equals(account.getAccountId())
                    || !request.context().brokerAccountId().equals(broker.id())
                    || !account.getBaseCurrency().equalsIgnoreCase(request.context().reportingCurrency())))
                return unavailable("MARGIN_CONTEXT_IDENTITY_MISMATCH");
            MarginPolicy policy = request.context() != null
                    ? policies.version(request.context().policyId(), request.context().policyVersion()).orElse(null)
                    : request.policyId() == null
                    ? policies.assigned(account.getAccountId(), request.instrument(), request.requestedAt()).orElse(null)
                    : (request.policyVersion() == null ? null
                    : policies.version(UUID.fromString(request.policyId()), request.policyVersion()).orElse(null));
            if (policy == null) return unavailable("MISSING_POLICY");
            if (!policy.instrument().equalsIgnoreCase(request.instrument())) return unavailable("POLICY_INSTRUMENT_MISMATCH");
            if (!policy.applicableAt(request.requestedAt())) return unavailable(policy.revoked() ? "POLICY_REVOKED" : "POLICY_EXPIRED");
            var contract = contracts.resolve(request.instrument()).requireProductType(
                    com.hope.trading.trading_core.shared.domain.model.InstrumentProductType.CFD);
            var exposure = exposures.resolve(account.getAccountId(), request.instrument(),
                    trades.findAllByAccount_AccountId(account.getAccountId()), request.requestedAt());
            if (!exposure.available()) return unavailable(exposure.reason());
            if (request.context() == null && request.exposureSnapshotId() != null
                    && !request.exposureSnapshotId().equals(exposure.snapshotId().toString())) return unavailable("EXPOSURE_SNAPSHOT_MISMATCH");
            if (exposure.contractId() != null && (!exposure.contractId().equals(contract.provenance().sourceId())
                    || !exposure.contractVersion().equals(contract.provenance().sourceVersion()))) return unavailable("CONTRACT_MISMATCH");
            var valuation = market.value(account.getBaseCurrency(), request.requestedAt(),
                    List.of(new MarketValuationPort.Instrument("proposed", request.instrument(),
                            MarketValuationPort.PriceUse.LAST)), List.of(),
                    MarketValuationPort.FreshnessRequirement.t0());
            var matches = valuation.facts().stream().filter(f -> "INSTRUMENT".equals(f.type())
                    && "proposed".equals(f.id()) && "AVAILABLE".equals(f.status())).toList();
            if (matches.size() != 1) return unavailable("VALUATION_FACT_AMBIGUOUS");
            var fact = matches.getFirst();
            if (fact.sourcePrice() == null || fact.sourcePrice().signum() <= 0) return unavailable("MISSING_MARK");
            Duration maxAge = Duration.parse(valuation.maxObservationAge());
            if (!valuation.complete() || valuation.sourceVersion() < 1
                    || !account.getBaseCurrency().equalsIgnoreCase(valuation.reportingCurrency())
                    || fact.sourceEffectiveAt() == null || fact.sourceCapturedAt() == null
                    || fact.sourceEffectiveAt().isAfter(request.requestedAt()) || fact.sourceCapturedAt().isAfter(request.requestedAt())
                    || fact.sourceEffectiveAt().isBefore(request.requestedAt().minus(maxAge))
                    || fact.sourceCapturedAt().isBefore(request.requestedAt().minus(maxAge))
                    || valuation.valuationTimestamp() == null || valuation.capturedAt() == null
                    || valuation.valuationTimestamp().isAfter(request.requestedAt())
                    || valuation.capturedAt().isAfter(request.requestedAt())) return unavailable("STALE_FACT");
            BigDecimal quoteRate = fact.quoteToReportingRate();
            if (quoteRate == null && contract.quoteCurrency().value().equalsIgnoreCase(account.getBaseCurrency())) quoteRate = BigDecimal.ONE;
            if (quoteRate == null) return unavailable("MISSING_FX");
            MarginResolutionContext context = new MarginResolutionContext(
                    account.getAccountId(), broker.id(), policy.id(), policy.version(), exposure.snapshotId(),
                    contract.provenance().sourceId(), contract.provenance().sourceVersion(), valuation.snapshotId(),
                    valuation.sourceVersion(), contract.quoteCurrency().value(), account.getBaseCurrency(), quoteRate,
                    maxAge, policy.reportingCurrencyScale(), request.requestedAt());
            if (!context.financialAccountId().equals(account.getAccountId()) || !context.brokerAccountId().equals(broker.id())) return unavailable("CONTEXT_IDENTITY_MISMATCH");
            var result = calculator.calculate(policy, new PaperMarginCalculator.Request(request.instrument(),
                    exposure.signedQuantity(), signed(request), request.price(), contract.contractMultiplier(),
                    contract.quoteCurrency().value(), account.getBaseCurrency(), fact.conversionLegs(), request.requestedAt(),
                    maxAge, context.quoteToReportingRate()));
            if (!result.available()) return unavailable(result.reason());
            var evidence = new RequiredMarginPort.MarginEvidence(null, policy.sourceCategory(), policy.sourceReference(),
                    context.contractId(), context.contractVersion(), context.exposureSnapshotId(), context.valuationSnapshotId(),
                    context.valuationSourceVersion(), context.quoteToReportingRate(), context.maxObservationAge().toString(),
                    context.reportingCurrencyScale());
            return RequiredMarginPort.Resolution.available(new RequiredMarginPort.Fact(result.amount(), result.currency(),
                    "paper-margin-policy:" + policy.id() + ":" + policy.version(),
                    stableVersion(policy.version()), request.requestedAt(), context, evidence));
        } catch (RuntimeException failure) {
            return unavailable(failure.getMessage() == null ? "MARGIN_CONTEXT_UNAVAILABLE" : failure.getMessage());
        }
    }

    public Optional<MarginResolutionContext> resolveContext(RequiredMarginPort.Request request) {
        return Optional.ofNullable(resolveDetailed(request).fact()).map(RequiredMarginPort.Fact::context);
    }

    private static RequiredMarginPort.Resolution unavailable(String reason) {
        return RequiredMarginPort.Resolution.unavailable(reason == null ? "MARGIN_CONTEXT_UNAVAILABLE" : reason);
    }

    private static long stableVersion(String version) {
        return Math.max(1L, version.hashCode() & 0x7fffffffL);
    }

    private static java.math.BigDecimal signed(RequiredMarginPort.Request request) {
        return "SHORT".equalsIgnoreCase(request.direction()) ? request.quantity().negate() : request.quantity();
    }
}
