import { MarketStreamType } from './market-stream-type';
import { OhlcInterval } from './ohlc-interval';

export interface OhlcEvent {
  marketId: string;
  provider: string;
  symbol: string;
  streamType: MarketStreamType.OHLC;

  interval: OhlcInterval;

  openTime: string;
  closeTime: string;

  open: number;
  high: number;
  low: number;
  close: number;

  volume: number;
  vwap: number;
  trades: number;

  closed: boolean;
  occurredAt: string;
}
