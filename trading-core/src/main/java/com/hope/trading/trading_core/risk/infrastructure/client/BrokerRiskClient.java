package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "broker-service", contextId = "brokerRiskFactsClient")
interface BrokerRiskFeignClient {
    @GetMapping("/internal/v1/broker-accounts/{id}/risk-snapshot")
    BrokerRiskTransport get(@PathVariable UUID id, @RequestParam Instant from, @RequestParam Instant to);
}

record BrokerRiskTransport(UUID brokerAccountId, long snapshotVersion, Instant observedAt,
                           String completeness, List<String> unavailabilityReasons,
                           Map<String, BigDecimal> assetBalances, Account account,
                           List<Position> positions, List<ClosedTrade> closedTrades,
                           List<LedgerEntry> ledgerEntries) {
    record Account(String valuationAsset, BigDecimal balance, BigDecimal equity, BigDecimal margin) { }
    record Position(UUID positionId, String providerPositionReference, String providerReferenceProvenance,
                    String instrument, BigDecimal signedQuantity, BigDecimal entryPrice, BigDecimal cost,
                    BigDecimal marketValue, BigDecimal unrealizedPnl, BigDecimal margin,
                    BigDecimal protectedQuantity, List<Stop> protectiveStops) { }
    record Stop(String providerOrderReference, String providerReferenceProvenance,
                BigDecimal quantity, BigDecimal stopPrice) { }
    record ClosedTrade(String providerTradeReference, String instrument, String settlementAsset,
                       String side, BigDecimal quantity,
                       BigDecimal price, BigDecimal fee, BigDecimal realizedPnl, Instant closedAt) { }
    record LedgerEntry(String providerLedgerReference, String asset, String type, BigDecimal amount,
                       BigDecimal fee, BigDecimal balance, Instant occurredAt) { }
}

@Component
public final class BrokerRiskClient implements BrokerRiskFactsPort {
    private final BrokerRiskFeignClient client;
    private final ObjectMapper mapper;

    public BrokerRiskClient(BrokerRiskFeignClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Snapshot load(UUID brokerAccountId, Instant from, Instant to) {
        BrokerRiskTransport value = client.get(brokerAccountId, from, to);
        try {
            var account = value.account() == null ? null : new Account(value.account().valuationAsset(),
                    value.account().balance(), value.account().equity(), value.account().margin());
            var positions = value.positions().stream().map(p -> new Position(p.positionId(),
                    p.providerPositionReference(), p.providerReferenceProvenance(), p.instrument(),
                    p.signedQuantity(), p.entryPrice(), p.marketValue(), p.margin(), p.protectedQuantity(),
                    p.protectiveStops().stream().map(s -> new Stop(s.providerOrderReference(),
                            s.providerReferenceProvenance(), s.quantity(), s.stopPrice())).toList())).toList();
            var closed = value.closedTrades().stream().map(t -> new ClosedTrade(t.providerTradeReference(),
                    t.instrument(), t.settlementAsset(), t.fee(), t.realizedPnl(), t.closedAt())).toList();
            var ledger = value.ledgerEntries().stream().map(e -> new LedgerEntry(e.providerLedgerReference(),
                    e.asset(), e.type(), e.amount(), e.fee(), e.balance(), e.occurredAt())).toList();
            return new Snapshot(value.brokerAccountId(), value.snapshotVersion(), value.observedAt(),
                    "COMPLETE".equals(value.completeness()), value.unavailabilityReasons(), value.assetBalances(),
                    account, positions, closed, ledger, mapper.writeValueAsString(value));
        } catch (Exception failure) {
            throw new IllegalStateException("Broker snapshot cannot be preserved", failure);
        }
    }
}
