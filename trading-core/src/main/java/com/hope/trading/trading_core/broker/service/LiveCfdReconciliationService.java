package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only comparison of provider-authoritative LIVE positions with durable
 * local provider linkage. It does not settle, close, or mutate either side.
 */
@Service
public final class LiveCfdReconciliationService {
    private final InstrumentFinancialContractResolver contracts;

    public LiveCfdReconciliationService(InstrumentFinancialContractResolver contracts) {
        this.contracts = contracts;
    }

    public Result unavailable(String reason) {
        return new Result(Status.UNAVAILABLE, null, List.of(), List.of(reason));
    }

    public Result reconcile(BrokerAccountSnapshotV2Dto snapshot, Collection<Trade> localTrades) {
        if (snapshot == null || !completeAndFresh(snapshot) || !accountFactsAvailable(snapshot)) {
            return unavailableResult("LIVE_PROVIDER_SNAPSHOT_UNAVAILABLE", snapshot);
        }

        Map<String, Trade> localByReference = new HashMap<>();
        boolean duplicateLocalReference = false;
        for (Trade trade : localTrades == null ? List.<Trade>of() : localTrades) {
            if (trade != null && trade.getTradeStatus() == TradeStatus.OPEN
                    && trade.getBrokerPositionReference() != null
                    && !trade.getBrokerPositionReference().isBlank()) {
                duplicateLocalReference |= localByReference.put(trade.getBrokerPositionReference(), trade) != null;
            }
        }
        if (duplicateLocalReference) {
            return unavailableResult("DUPLICATE_LOCAL_PROVIDER_POSITION_REFERENCE", snapshot);
        }

        Map<String, BrokerPositionSnapshotV2Dto> providerByReference = new HashMap<>();
        List<PositionResult> positions = new java.util.ArrayList<>();
        for (BrokerPositionSnapshotV2Dto position : snapshot.positions()) {
            String reference = position == null ? null : position.brokerPositionReference();
            if (reference != null && !reference.isBlank() && providerByReference.put(reference, position) != null) {
                return new Result(Status.UNAVAILABLE, accountFacts(snapshot), List.of(), List.of(
                        "DUPLICATE_PROVIDER_POSITION_REFERENCE:" + reference));
            }
            positions.add(compareProviderPosition(position,
                    reference == null ? null : localByReference.remove(reference)));
        }

        localByReference.values().forEach(trade -> positions.add(new PositionResult(
                Status.LOCAL_ONLY, trade.getBrokerPositionReference(), trade.getSymbol(),
                "LOCAL_PROVIDER_POSITION_NOT_OBSERVED")));

        Status overall = positions.stream().map(PositionResult::status)
                .filter(status -> status != Status.MATCHED)
                .findFirst().orElse(Status.MATCHED);
        return new Result(overall, accountFacts(snapshot), List.copyOf(positions), List.of());
    }

    private PositionResult compareProviderPosition(BrokerPositionSnapshotV2Dto provider, Trade local) {
        if (provider == null || provider.brokerPositionReference() == null || provider.brokerPositionReference().isBlank()
                || provider.instrument() == null || provider.signedQuantity() == null
                || provider.signedQuantity().signum() == 0 || provider.entryPrice() == null) {
            return unavailable(provider, "PROVIDER_POSITION_FACTS_INCOMPLETE");
        }
        com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract contract;
        try {
            contract = contracts.resolveForInstrument(provider.instrument()).orElse(null);
            if (contract == null || contract.productType() != InstrumentProductType.CFD) {
                return unavailable(provider, "CFD_PRODUCT_CLASSIFICATION_UNAVAILABLE");
            }
        } catch (RuntimeException unavailable) {
            return unavailable(provider, "CFD_CONTRACT_UNAVAILABLE");
        }
        if (local == null) {
            return new PositionResult(Status.PROVIDER_ONLY, provider.brokerPositionReference(),
                    provider.instrument(), "PROVIDER_POSITION_NOT_LINKED_LOCALLY");
        }
        if (local.getProductType() != InstrumentProductType.CFD
                || contract == null
                || !contract.instrument().equalsIgnoreCase(local.getSymbol())
                || !hasCompleteContract(local)
                || !contractMatches(local, contract)) {
            return new PositionResult(Status.MISMATCH, provider.brokerPositionReference(),
                    provider.instrument(), "POSITION_IDENTITY_OR_PRODUCT_MISMATCH");
        }
        BigDecimal localSignedQuantity = local.getType() == TradeType.BUY
                ? local.getQuantity() : local.getQuantity() == null ? null : local.getQuantity().negate();
        if (localSignedQuantity == null || localSignedQuantity.compareTo(provider.signedQuantity()) != 0
                || local.getEntryPrice() == null || local.getEntryPrice().compareTo(provider.entryPrice()) != 0) {
            return new PositionResult(Status.MISMATCH, provider.brokerPositionReference(),
                    provider.instrument(), "POSITION_FINANCIAL_FACT_MISMATCH");
        }
        return new PositionResult(Status.MATCHED, provider.brokerPositionReference(),
                provider.instrument(), "");
    }

    private boolean hasCompleteContract(Trade trade) {
        return trade.getBaseCurrency() != null && !trade.getBaseCurrency().isBlank()
                && trade.getQuoteCurrency() != null && !trade.getQuoteCurrency().isBlank()
                && trade.getQuantityUnit() != null
                && trade.getContractMultiplier() != null && trade.getContractMultiplier().signum() > 0
                && trade.getPricePrecision() != null && trade.getPricePrecision() >= 0
                && trade.getQuantityPrecision() != null && trade.getQuantityPrecision() >= 0
                && trade.getMinimumQuantity() != null && trade.getMinimumQuantity().signum() > 0
                && trade.getQuantityIncrement() != null && trade.getQuantityIncrement().signum() > 0
                && trade.getContractSourceId() != null && !trade.getContractSourceId().isBlank()
                && trade.getContractSourceVersion() != null && trade.getContractSourceVersion() >= 1
                && trade.getContractObservedAt() != null;
    }

    private boolean contractMatches(Trade trade,
                                    com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract contract) {
        return trade.getBaseCurrency().equalsIgnoreCase(contract.baseCurrency().value())
                && trade.getQuoteCurrency().equalsIgnoreCase(contract.quoteCurrency().value())
                && trade.getQuantityUnit() == contract.quantityUnit()
                && trade.getContractMultiplier().compareTo(contract.contractMultiplier()) == 0
                && trade.getPricePrecision() == contract.pricePrecision()
                && trade.getQuantityPrecision() == contract.quantityPrecision()
                && trade.getMinimumQuantity().compareTo(contract.minimumQuantity()) == 0
                && trade.getQuantityIncrement().compareTo(contract.quantityIncrement()) == 0
                && trade.getContractSourceId().equals(contract.provenance().sourceId())
                && trade.getContractSourceVersion() == contract.provenance().sourceVersion()
                && trade.getContractObservedAt().equals(contract.provenance().observedAt());
    }

    private PositionResult unavailable(BrokerPositionSnapshotV2Dto position, String reason) {
        return new PositionResult(Status.UNAVAILABLE,
                position == null ? null : position.brokerPositionReference(),
                position == null ? null : position.instrument(), reason);
    }

    private boolean completeAndFresh(BrokerAccountSnapshotV2Dto snapshot) {
        return "COMPLETE".equals(snapshot.completeness()) && "FRESH".equals(snapshot.freshness())
                && snapshot.balances() != null
                && snapshot.positions() != null
                && snapshot.orders() != null
                && snapshot.deals() != null;
    }

    private boolean accountFactsAvailable(BrokerAccountSnapshotV2Dto snapshot) {
        var account = snapshot.account();
        return account != null && account.valuationAsset() != null && !account.valuationAsset().isBlank()
                && account.balance() != null && account.equity() != null
                && account.usedMargin() != null && account.freeMargin() != null
                && snapshot.observedAt() != null && snapshot.observationStartedAt() != null
                && snapshot.completedAt() != null;
    }

    private Result unavailableResult(String reason, BrokerAccountSnapshotV2Dto snapshot) {
        return new Result(Status.UNAVAILABLE, accountFacts(snapshot), List.of(), List.of(reason));
    }

    private AccountFacts accountFacts(BrokerAccountSnapshotV2Dto snapshot) {
        if (snapshot == null || snapshot.account() == null) return null;
        var account = snapshot.account();
        return new AccountFacts(snapshot.providerRegistrationReference(), snapshot.externalAccountId(),
                account.valuationAsset(), account.balance(), account.equity(), account.usedMargin(),
                account.freeMargin(), snapshot.observedAt(), snapshot.observationStartedAt(),
                snapshot.completedAt(), snapshot.completeness(), snapshot.freshness());
    }

    public enum Status { MATCHED, PROVIDER_ONLY, LOCAL_ONLY, MISMATCH, UNAVAILABLE }

    public record PositionResult(Status status, String providerPositionReference,
                                 String instrument, String reason) { }

    public record AccountFacts(java.util.UUID providerRegistrationReference, String externalAccountId,
                               String valuationAsset, BigDecimal balance, BigDecimal equity,
                               BigDecimal usedMargin, BigDecimal freeMargin,
                               java.time.Instant observedAt, java.time.Instant observationStartedAt,
                               java.time.Instant completedAt, String completeness, String freshness) { }

    public record Result(Status status, AccountFacts account, List<PositionResult> positions, List<String> reasons) {
        public Result(Status status, List<PositionResult> positions, List<String> reasons) {
            this(status, null, positions, reasons);
        }

        public Result {
            positions = List.copyOf(positions);
            reasons = List.copyOf(reasons);
        }
    }
}
