export interface MarketFilter {
  search: string;
  provider?: string;
  baseAsset?: string;
  quoteAsset?: string;
  tradingStatus?: string;
  tradable?: boolean;
}

export type MarketSortField =
  'SYMBOL' | 'PROVIDER' | 'BASE_ASSET' | 'QUOTE_ASSET' | 'STATUS' | 'TRADABILITY';

export type MarketSortDirection = 'ASC' | 'DESC';

export interface MarketSort {
  field: MarketSortField;
  direction: MarketSortDirection;
}

export const DEFAULT_MARKET_SORT: MarketSort = {
  field: 'SYMBOL',
  direction: 'ASC',
};
