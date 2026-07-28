export interface OrderBookLevel {
  price: number;
  quantity: number;
}

export interface OrderBookSnapshot {
  marketId: string;
  provider: string;
  symbol: string;
  streamType: 'ORDER_BOOK';
  depth: number;
  bids: ReadonlyArray<OrderBookLevel>;
  asks: ReadonlyArray<OrderBookLevel>;
  bestBid: number | null;
  bestAsk: number | null;
  spread: number | null;
  bidVolume: number;
  askVolume: number;
  imbalance: number;
  occurredAt: string;
}
