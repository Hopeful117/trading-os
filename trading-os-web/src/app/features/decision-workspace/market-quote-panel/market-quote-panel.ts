import { DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';

import { MarketResponse } from '../../../core/models/market-response';
import { TickerEvent } from '../../../core/models/ticker-event.model';
import { FreshnessIndicator } from '../../../shared/workstation/freshness-indicator/freshness-indicator';
import { MetricValue } from '../../../shared/workstation/metric-value/metric-value';
import {
  StatusBadge,
  StatusBadgeTone,
} from '../../../shared/workstation/status-badge/status-badge';
import { WorkspacePanelHeader } from '../../../shared/workstation/workspace-panel-header/workspace-panel-header';
import { WorkspacePanel } from '../../../shared/workstation/workspace-panel/workspace-panel';
import {
  calculateMarketSpread,
  formatMarketPrice,
  marketPricePrecision,
} from '../../../core/utils/market-price';

export type MarketQuoteStreamView =
  { status: 'waiting' } | { status: 'live'; data: TickerEvent } | { status: 'error' };

export type MarketQuoteFreshness = 'LIVE' | 'RECENT' | 'STALE' | 'UNAVAILABLE';

@Component({
  selector: 'app-market-quote-panel',
  imports: [
    DatePipe,
    FreshnessIndicator,
    MetricValue,
    StatusBadge,
    WorkspacePanel,
    WorkspacePanelHeader,
  ],
  standalone: true,
  templateUrl: './market-quote-panel.html',
  styleUrl: './market-quote-panel.scss',
})
export class MarketQuotePanel {
  @Input({ required: true }) market!: MarketResponse;
  @Input({ required: true }) ticker!: MarketQuoteStreamView;
  @Input({ required: true }) freshness!: MarketQuoteFreshness;

  formatPrice(value: number): string {
    return formatMarketPrice(value, marketPricePrecision(this.market));
  }

  spread(bid: number, ask: number): string {
    const precision = marketPricePrecision(this.market);
    return formatMarketPrice(calculateMarketSpread(bid, ask, precision), precision);
  }

  marketStatusTone(): StatusBadgeTone {
    return this.market.marketState.tradable ? 'positive' : 'warning';
  }
}
