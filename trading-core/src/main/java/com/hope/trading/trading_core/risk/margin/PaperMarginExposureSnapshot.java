package com.hope.trading.trading_core.risk.margin;

import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/** Validated read-side net exposure; it never changes settlement trades. */
public final class PaperMarginExposureSnapshot {
    public Result resolve(UUID accountId, String instrument, List<Trade> trades, Instant asOf) {
        if (accountId == null || instrument == null || instrument.isBlank() || trades == null || asOf == null)
            return Result.unavailable("EXPOSURE_UNAVAILABLE");
        BigDecimal total = BigDecimal.ZERO;
        int direction = 0;
        String contractId = null;
        Long contractVersion = null;
        List<String> identities = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade == null || trade.getTradeId() == null || trade.getAccount() == null || !accountId.equals(trade.getAccount().getAccountId())
                    || !instrument.equalsIgnoreCase(trade.getSymbol()) || trade.getTradeStatus() != TradeStatus.OPEN
                    || trade.getProductType() != InstrumentProductType.CFD || trade.getQuantity() == null
                    || trade.getQuantity().signum() == 0 || trade.getContractMultiplier() == null
                    || trade.getContractMultiplier().signum() <= 0 || trade.getContractSourceId() == null
                    || trade.getContractSourceVersion() == null || trade.getOpenedAt() == null
                    || trade.getOpenedAt().isAfter(asOf) || trade.getType() == null
                    || trade.getQuoteCurrency() == null || trade.getBaseCurrency() == null
                    || trade.getQuantityUnit() == null)
                return Result.unavailable("AMBIGUOUS_EXPOSURE");
            String currentId = trade.getContractSourceId();
            Long currentVersion = trade.getContractSourceVersion();
            if (contractId != null && (!contractId.equals(currentId) || !contractVersion.equals(currentVersion)))
                return Result.unavailable("AMBIGUOUS_EXPOSURE");
            contractId = currentId;
            contractVersion = currentVersion;
            int tradeDirection = trade.getQuantity().signum();
            if ((trade.getType() == TradeType.BUY && tradeDirection < 0)
                    || (trade.getType() == TradeType.SELL && tradeDirection > 0)) {
                return Result.unavailable("AMBIGUOUS_EXPOSURE");
            }
            if (direction != 0 && direction != tradeDirection) return Result.unavailable("HEDGING_UNSUPPORTED");
            direction = tradeDirection;
            total = total.add(trade.getQuantity());
            identities.add(String.join("|", trade.getTradeId().toString(), Long.toString(trade.getVersion()),
                    trade.getSymbol(), trade.getProductType().name(), trade.getQuoteCurrency(),
                    trade.getBaseCurrency(), trade.getQuantityUnit().name(), trade.getContractMultiplier().toPlainString(),
                    trade.getContractSourceId(), trade.getContractSourceVersion().toString(), trade.getOpenedAt().toString(),
                    trade.getType().name(), trade.getQuantity().toPlainString()));
        }
        Collections.sort(identities);
        String identity = accountId + "|" + instrument.toUpperCase() + "|" + asOf + "|" + String.join(";", identities);
        UUID snapshotId = UUID.nameUUIDFromBytes(identity
                .getBytes(StandardCharsets.UTF_8));
        return Result.available(total, snapshotId, contractId, contractVersion, asOf);
    }

    public record Result(boolean available, BigDecimal signedQuantity, UUID snapshotId,
                         String contractId, Long contractVersion, Instant observedAt, String reason) {
        static Result available(BigDecimal quantity, UUID id, String contractId, Long version, Instant observedAt) {
            return new Result(true, quantity, id, contractId, version, observedAt, null);
        }
        static Result unavailable(String reason) { return new Result(false, null, null, null, null, null, reason); }
    }
}
