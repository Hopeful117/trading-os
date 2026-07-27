import { MarketStreamParameters } from './market-stream-parameters';
import { MarketStreamType } from './market-stream-type';

export interface MarketStreamRequest {

  type: MarketStreamType;
  parameters: MarketStreamParameters | null;
}
