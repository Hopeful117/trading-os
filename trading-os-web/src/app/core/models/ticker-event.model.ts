export interface TickerEvent {
  marketId: string;
  provider: string;
  symbol: string;
  streamType: 'TICKER';
  bid: number;
  ask: number;
  last: number;
  volume: number | null;
  occurredAt: string;
}
