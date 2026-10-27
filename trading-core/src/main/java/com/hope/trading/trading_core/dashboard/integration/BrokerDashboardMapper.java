package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.dto.Position;
import com.hope.trading.trading_core.helper.TradeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class BrokerDashboardMapper {
    public BrokerAccountFact toFact(BrokerAccountDto dto) {
        boolean balancesAvailable = dto.getBalances() != null
                && dto.getBalances().getBalances() != null;
        Map<String, BigDecimal> balances = balancesAvailable
                ? dto.getBalances().getBalances() : Map.of();
        List<PositionFact> positions = dto.getOpenTrades() == null
                ? List.of()
                : dto.getOpenTrades().stream().map(this::toPositionFact).toList();

        return new BrokerAccountFact(
                dto.getBrokerAccountId(),
                dto.getBroker(),
                dto.getBaseCurrency(),
                balances,
                balancesAvailable,
                dto.getBrokerEquity(),
                dto.isBrokerEquityTotal(),
                positions,
                dto.getDataAt() == null ? Instant.now() : dto.getDataAt(),
                PositionPnlTreatment.ADDITIVE
        );
    }

    public BrokerAccountFact toFact(BrokerAccountSnapshotV2Dto snapshot, String broker, String fallbackCurrency) {
        var account = snapshot.account();
        String currency = account != null && account.valuationAsset() != null
                ? account.valuationAsset() : fallbackCurrency;
        BigDecimal equity = account == null ? null : account.equity();
        boolean complete = "COMPLETE".equals(snapshot.completeness())
                && "FRESH".equals(snapshot.freshness());
        List<PositionFact> positions = snapshot.positions() == null
                ? List.of() : snapshot.positions().stream().map(this::toPositionFact).toList();

        return new BrokerAccountFact(
                snapshot.brokerAccountId().toString(), broker, currency,
                snapshot.balances(), snapshot.balances() != null,
                equity, complete && equity != null, positions,
                snapshot.observedAt(), PositionPnlTreatment.ADDITIVE
        );
    }

    private PositionFact toPositionFact(BrokerPositionSnapshotV2Dto position) {
        BigDecimal signedQuantity = position.signedQuantity();
        TradeType side = signedQuantity != null && signedQuantity.signum() < 0
                ? TradeType.SELL : TradeType.BUY;
        BigDecimal quantity = signedQuantity == null ? null : signedQuantity.abs();
        return new PositionFact(
                position.brokerPositionReference(), position.instrument(), side, quantity,
                position.entryPrice(), position.stopLoss(), null, null, position.margin(), null,
                position.observedAt(), position.observedAt()
        );
    }

    public PositionFact toPositionFact(Position position) {
        BigDecimal entryPrice = position.getEntryPrice();
        if (entryPrice == null
                && position.getEntryValue() != null
                && position.getQuantity() != null
                && position.getQuantity().signum() != 0) {
            entryPrice = position.getEntryValue().divide(
                    position.getQuantity(), MathContext.DECIMAL128
            );
        }

        return new PositionFact(
                position.getBrokerPositionId(),
                position.getSymbol(),
                TradeType.valueOf(position.getSide().toUpperCase()),
                position.getQuantity(),
                entryPrice,
                position.getStopLoss(),
                position.getTakeProfit(),
                position.getUnrealizedPnl(),
                position.getMargin(),
                position.getExposure(),
                position.getOpenedAt(),
                position.getDataAt()
        );
    }
}
