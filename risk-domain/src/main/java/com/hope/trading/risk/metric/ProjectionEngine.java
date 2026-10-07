package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.ProposedTrade;
import com.hope.trading.risk.domain.RiskTypes.ProtectionStatus;
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
        Money heat = sumKnownLoss(projectedPositions, currency);
        BigDecimal projectedEquity = account.equity().amount().subtract(heat.amount());
        Money projectedDrawdown = new Money(account.dailyRiskBaseline().referenceBalance().amount()
                .subtract(projectedEquity).max(BigDecimal.ZERO), currency);
        Optional<Money> projectedTotalDrawdown = account.accountStartingBalance()
                .map(starting -> new Money(starting.amount().subtract(projectedEquity)
                        .max(BigDecimal.ZERO), currency));
        Money currentPositionMargin = portfolio.positions().stream()
                .map(PositionSnapshot::marginUsed)
                .reduce(Money.zero(currency), Money::add);
        Money projectedPositionMargin =
                sum(projectedPositions, currency, ProjectedPosition::margin);
        Money margin = account.usedMargin()
                .subtract(currentPositionMargin).add(projectedPositionMargin);
        return new ProjectedMetrics(exposure, projectedDrawdown, projectedTotalDrawdown, margin, heat,
                new ProjectedPortfolioState(projectedPositions));
    }

    private Map<String, PositionValues> aggregate(PortfolioSnapshot portfolio, String currency) {
        Map<String, PositionValues> result = new HashMap<>();
        for (PositionSnapshot position : portfolio.positions()) {
            requireCurrency(currency, position.marketValue());
            position.lossAtStop().ifPresent(loss -> requireCurrency(currency, loss));
            requireCurrency(currency, position.marginUsed());
            result.merge(position.instrument(),
                    new PositionValues(position.signedQuantity(),
                            position.marketValue().amount(), position.lossAtStop().map(Money::amount),
                            position.marginUsed().amount(), position.protectionStatus()), PositionValues::add);
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
                trade.notional().amount(), Optional.of(trade.expectedLossAtStop().amount()),
                trade.marginRequired().amount(), ProtectionStatus.PROTECTED);
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

    private Money sumKnownLoss(List<ProjectedPosition> positions, String currency) {
        return positions.stream().map(ProjectedPosition::lossAtStop).flatMap(Optional::stream)
                .reduce(Money.zero(currency), Money::add);
    }

    private void requireCurrency(String expected, Money money) {
        if (!expected.equals(money.currency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }

    private record PositionValues(BigDecimal quantity, BigDecimal exposure,
                                  Optional<BigDecimal> loss, BigDecimal margin,
                                  ProtectionStatus protectionStatus) {
        PositionValues add(PositionValues other) {
            return new PositionValues(quantity.add(other.quantity),
                    exposure.add(other.exposure), addKnownLoss(loss, other.loss),
                    margin.add(other.margin), mergeProtection(protectionStatus, other.protectionStatus));
        }
        PositionValues scaleToQuantity(BigDecimal newQuantity,
                                       BigDecimal originalAbsoluteQuantity) {
            BigDecimal newAbsolute = newQuantity.abs();
            return new PositionValues(newQuantity,
                    exposure.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128),
                    loss.map(value -> value.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128)),
                    margin.divide(originalAbsoluteQuantity, MathContext.DECIMAL128)
                            .multiply(newAbsolute, MathContext.DECIMAL128), protectionStatus);
        }
        ProjectedPosition toProjectedPosition(String instrument, String currency) {
            return new ProjectedPosition(instrument, quantity,
                    new Money(exposure, currency), loss.map(value -> new Money(value, currency)),
                    new Money(margin, currency), protectionStatus);
        }

        private static Optional<BigDecimal> addKnownLoss(Optional<BigDecimal> left,
                                                         Optional<BigDecimal> right) {
            if (left.isEmpty()) return right;
            if (right.isEmpty()) return left;
            return Optional.of(left.get().add(right.get()));
        }

        private static ProtectionStatus mergeProtection(ProtectionStatus left, ProtectionStatus right) {
            if (left == right) return left;
            if (left == ProtectionStatus.UNKNOWN || right == ProtectionStatus.UNKNOWN) {
                return ProtectionStatus.UNKNOWN;
            }
            return ProtectionStatus.PARTIALLY_PROTECTED;
        }
    }
}
