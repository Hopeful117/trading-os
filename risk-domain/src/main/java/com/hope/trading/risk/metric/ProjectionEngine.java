package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.ProposedTrade;
import com.hope.trading.risk.domain.RiskTypes.TradeDirection;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import com.hope.trading.risk.snapshot.PortfolioSnapshot;
import com.hope.trading.risk.snapshot.PositionSnapshot;
import java.math.*;
import java.util.*;

/** Deterministically projects the net portfolio after applying one trade delta. */
public final class ProjectionEngine {
    public ProjectedMetrics project(AccountSnapshot account, PortfolioSnapshot portfolio,
                                    ProposedTrade trade) {
        String currency = account.balance().currency();
        Map<String, PositionValues> positions = aggregate(portfolio, currency);
        if (trade != null) apply(positions, trade, currency);
        List<ProjectedPosition> projectedPositions = positions.entrySet().stream()
                .filter(e -> e.getValue().quantity.signum() != 0)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().toProjectedPosition(e.getKey(), currency))
                .toList();
        Money exposure = sum(projectedPositions, currency, ProjectedPosition::exposure);
        Money heat = sum(projectedPositions, currency, ProjectedPosition::lossAtStop);
        Money observedDailyLoss = new Money(account.dailyStartBalance().amount()
                .subtract(account.equity().amount()).max(BigDecimal.ZERO), currency);
        Money projectedDrawdown = observedDailyLoss.add(heat);
        Money currentPositionMargin = portfolio.positions().stream()
                .map(PositionSnapshot::marginUsed)
                .reduce(Money.zero(currency), Money::add);
        Money projectedPositionMargin =
                sum(projectedPositions, currency, ProjectedPosition::margin);
        Money margin = account.usedMargin()
                .subtract(currentPositionMargin).add(projectedPositionMargin);
        return new ProjectedMetrics(exposure, projectedDrawdown, margin, heat,
                new ProjectedPortfolioState(projectedPositions));
    }

    private Map<String, PositionValues> aggregate(PortfolioSnapshot portfolio, String currency) {
        Map<String, PositionValues> result = new HashMap<>();
        for (PositionSnapshot position : portfolio.positions()) {
            requireCurrency(currency, position.marketValue());
            requireCurrency(currency, position.lossAtStop());
            requireCurrency(currency, position.marginUsed());
            result.merge(position.instrument(),
                    new PositionValues(position.signedQuantity(),
                            position.marketValue().amount(), position.lossAtStop().amount(),
                            position.marginUsed().amount()), PositionValues::add);
        }
        return result;
    }

    private void apply(Map<String, PositionValues> positions, ProposedTrade trade,
                       String currency) {
        requireCurrency(currency, trade.notional());
        requireCurrency(currency, trade.expectedLossAtStop());
        requireCurrency(currency, trade.marginRequired());
        BigDecimal signedDelta = trade.direction() == TradeDirection.LONG
                ? trade.quantity() : trade.quantity().negate();
        PositionValues current = positions.get(trade.instrument());
        PositionValues proposed = new PositionValues(signedDelta,
                trade.notional().amount(), trade.expectedLossAtStop().amount(),
                trade.marginRequired().amount());
        if (current == null || current.quantity.signum() == signedDelta.signum()) {
            positions.merge(trade.instrument(), proposed, PositionValues::add);
            return;
        }

        BigDecimal currentAbsolute = current.quantity.abs();
        BigDecimal deltaAbsolute = signedDelta.abs();
        int comparison = deltaAbsolute.compareTo(currentAbsolute);
        if (comparison < 0) {
            positions.put(trade.instrument(), current.scaleToQuantity(
                    current.quantity.add(signedDelta), currentAbsolute));
        } else if (comparison == 0) {
            positions.remove(trade.instrument());
        } else {
            positions.put(trade.instrument(), proposed.scaleToQuantity(
                    current.quantity.add(signedDelta), deltaAbsolute));
        }
    }

    private Money sum(List<ProjectedPosition> positions, String currency,
                      java.util.function.Function<ProjectedPosition, Money> extractor) {
        return positions.stream().map(extractor)
                .reduce(Money.zero(currency), Money::add);
    }

    private void requireCurrency(String expected, Money money) {
        if (!expected.equals(money.currency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }

    private record PositionValues(BigDecimal quantity, BigDecimal exposure,
                                  BigDecimal loss, BigDecimal margin) {
        PositionValues add(PositionValues other) {
            return new PositionValues(quantity.add(other.quantity),
                    exposure.add(other.exposure), loss.add(other.loss),
                    margin.add(other.margin));
        }
        PositionValues scaleToQuantity(BigDecimal newQuantity,
                                       BigDecimal originalAbsoluteQuantity) {
            BigDecimal newAbsolute = newQuantity.abs();
            return new PositionValues(newQuantity,
                    exposure.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128),
                    loss.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128),
                    margin.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128));
        }
        ProjectedPosition toProjectedPosition(String instrument, String currency) {
            return new ProjectedPosition(instrument, quantity,
                    new Money(exposure, currency), new Money(loss, currency),
                    new Money(margin, currency));
        }
    }
}
