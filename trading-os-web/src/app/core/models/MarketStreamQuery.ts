import { MarketStreamType } from './market-stream-type';

export interface MarketStreamQuery {
  marketId?: string;
  symbol: string;
  type: MarketStreamType;
  interval?: number;
}
