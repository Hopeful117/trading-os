package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
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
