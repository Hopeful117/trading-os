export type TradeSide = 'BUY' | 'SELL';

export interface TradeEvent {
  marketId: string;
  provider: string;
  symbol: string;
  streamType: 'TRADES';
  tradeId: string;
  side: TradeSide;
  price: number;
  quantity: number;
  notional: number;
  occurredAt: string;
}

export interface RecentTradesSnapshot {
  marketId: string;
  provider: string;
  symbol: string;
  trades: ReadonlyArray<TradeEvent>;
  generatedAt: string;
}
