import { Injectable } from '@angular/core';

import {
  DEFAULT_MARKET_SORT,
  MarketFilter,
  MarketSort,
  MarketSortField,
} from '../models/market-filter.model';
import { MarketResponse } from '../models/market-response';

export interface MarketDiscoveryIdentity {
  symbol?: string | null;
  baseAsset?: string | null;
  quoteAsset?: string | null;
  provider?: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class MarketDiscoveryService {
  filterAndSort(
    markets: readonly MarketResponse[],
    filter: MarketFilter,
    sort: MarketSort = DEFAULT_MARKET_SORT,
  ): MarketResponse[] {
    return [...markets]
      .filter((market) => this.matchesFilter(market, filter))
      .sort((left, right) => this.compareMarkets(left, right, sort));
  }

  sortMarkets(
    markets: readonly MarketResponse[],
    sort: MarketSort = DEFAULT_MARKET_SORT,
  ): MarketResponse[] {
    return [...markets].sort((left, right) => this.compareMarkets(left, right, sort));
  }

  matchesSearch(market: MarketDiscoveryIdentity, search: string): boolean {
    const query = search.trim().toLowerCase();
    if (!query) {
      return true;
    }

    return [market.symbol, market.baseAsset, market.quoteAsset, market.provider].some((value) =>
      value?.toLowerCase().includes(query),
    );
  }

  private matchesFilter(market: MarketResponse, filter: MarketFilter): boolean {
    return (
      this.matchesSearch(market, filter.search) &&
      this.matchesExact(market.provider, filter.provider) &&
      this.matchesExact(market.baseAsset, filter.baseAsset) &&
      this.matchesExact(market.quoteAsset, filter.quoteAsset) &&
      this.matchesExact(market.marketState?.tradingStatus, filter.tradingStatus) &&
      (filter.tradable === undefined || market.marketState?.tradable === filter.tradable)
    );
  }

  private matchesExact(value: string | null | undefined, expected: string | undefined): boolean {
    return expected === undefined || value === expected;
  }

  private compareMarkets(left: MarketResponse, right: MarketResponse, sort: MarketSort): number {
    const primary = this.compareField(left, right, sort.field);
    if (primary !== 0) {
      return sort.direction === 'DESC' ? -primary : primary;
    }

    return this.compareText(left.marketId, right.marketId);
  }

  private compareField(
    left: MarketResponse,
    right: MarketResponse,
    field: MarketSortField,
  ): number {
    switch (field) {
      case 'PROVIDER':
        return this.compareText(left.provider, right.provider);
      case 'BASE_ASSET':
        return this.compareText(left.baseAsset, right.baseAsset);
      case 'QUOTE_ASSET':
        return this.compareText(left.quoteAsset, right.quoteAsset);
      case 'STATUS':
        return this.compareText(left.marketState?.tradingStatus, right.marketState?.tradingStatus);
      case 'TRADABILITY':
        return this.compareBoolean(left.marketState?.tradable, right.marketState?.tradable);
      case 'SYMBOL':
      default:
        return this.compareText(left.symbol, right.symbol);
    }
  }

  private compareText(left: string | null | undefined, right: string | null | undefined): number {
    const normalizedLeft = (left ?? '').toLowerCase();
    const normalizedRight = (right ?? '').toLowerCase();
    if (normalizedLeft < normalizedRight) return -1;
    if (normalizedLeft > normalizedRight) return 1;
    return 0;
  }

  private compareBoolean(
    left: boolean | null | undefined,
    right: boolean | null | undefined,
  ): number {
    return Number(left ?? false) - Number(right ?? false);
  }
}
