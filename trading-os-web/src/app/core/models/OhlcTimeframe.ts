import { OhlcInterval } from './ohlc-interval';

interface OhlcTimeframe {
  label: string;
  minutes: number;
  interval: OhlcInterval;
}
