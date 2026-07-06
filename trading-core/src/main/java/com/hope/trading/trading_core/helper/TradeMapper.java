package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.model.Trade;
import org.springframework.stereotype.Component;

@Component
public class TradeMapper {
    public TradeDto toDto(Trade trade){
        return TradeDto.builder()
                .tradeId(trade.getTradeId())
                .symbol(trade.getSymbol())
                .type(trade.getType())
                .entryPrice(trade.getEntryPrice())
                .exitPrice(trade.getExitPrice())
                .quantity(trade.getQuantity())
                .pnl(trade.getPnl())
                .openedAt(trade.getOpenedAt())
                .closedAt(trade.getClosedAt())
                .stopLoss(trade.getStopLoss())
                .takeProfit(trade.getTakeProfit())
                .riskAmount(trade.getRiskAmount())
                .rewardAmount(trade.getRewardAmount())
                .riskRewardRatio(trade.getRiskRewardRatio())
                .build();
    }

    public Trade toEntity(TradeRequest tradeRequest){
        return Trade.builder()
                .symbol(tradeRequest.getSymbol())
                .type(tradeRequest.getType())
                .entryPrice(tradeRequest.getEntryPrice())
                .quantity(tradeRequest.getQuantity())
                .stopLoss(tradeRequest.getStopLoss())
                .takeProfit(tradeRequest.getTakeProfit())
                .build();
    }
}
