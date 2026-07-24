export interface MarketDataEvent {
  symbol: string;
  bid: number;
  ask: number;
  last: number;
  timestamp: string;
}
