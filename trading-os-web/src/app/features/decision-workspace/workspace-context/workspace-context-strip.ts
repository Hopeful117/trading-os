import { DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';

import { DecisionContextAccount } from '../../../core/models/decision-context.model';
import { MarketResponse, marketProductTypeLabel } from '../../../core/models/market-response';
import { OpportunityResponse } from '../../../core/models/opportunity.model';
import { FreshnessIndicator } from '../../../shared/workstation/freshness-indicator/freshness-indicator';
import { MetricValue } from '../../../shared/workstation/metric-value/metric-value';
import {
  StatusBadge,
  StatusBadgeTone,
} from '../../../shared/workstation/status-badge/status-badge';
import { WorkspacePanelHeader } from '../../../shared/workstation/workspace-panel-header/workspace-panel-header';
import { WorkspacePanel } from '../../../shared/workstation/workspace-panel/workspace-panel';

export type WorkspaceContextAccountState = 'loading' | 'selected' | 'unavailable' | 'error';
export type WorkspaceContextMarketState =
  'unselected' | 'loading' | 'ready' | 'ineligible' | 'not-tradable' | 'unavailable' | 'error';
export type WorkspaceContextFreshness = 'LIVE' | 'RECENT' | 'STALE' | 'UNAVAILABLE';
export type WorkspaceContextWorkflowStage =
  | 'Select account'
  | 'Resolve account context'
  | 'Select market'
  | 'Review opportunity'
  | 'Prepare Trade Plan';

export interface WorkspaceContextView {
  account: DecisionContextAccount | null;
  accountState: WorkspaceContextAccountState;
  market: MarketResponse | null;
  marketState: WorkspaceContextMarketState;
  opportunity: OpportunityResponse | null;
  freshness: WorkspaceContextFreshness;
  workflowStage: WorkspaceContextWorkflowStage;
  nextAction: string;
}

@Component({
  selector: 'app-workspace-context-strip',
  imports: [
    DatePipe,
    FreshnessIndicator,
    MetricValue,
    StatusBadge,
    WorkspacePanel,
    WorkspacePanelHeader,
  ],
  standalone: true,
  templateUrl: './workspace-context-strip.html',
  styleUrl: './workspace-context-strip.scss',
})
export class WorkspaceContextStrip {
  @Input({ required: true }) view!: WorkspaceContextView;

  readonly productTypeLabel = marketProductTypeLabel;

  accountValue(): string {
    switch (this.view.accountState) {
      case 'selected':
        return this.view.account?.name ?? 'UNKNOWN';
      case 'loading':
        return 'Loading account context';
      case 'error':
        return 'Unavailable';
      default:
        return 'No account selected';
    }
  }

  accountDetail(): string {
    switch (this.view.accountState) {
      case 'selected':
        return `${this.view.account?.baseCurrency ?? 'Unknown currency'} · account context resolved`;
      case 'loading':
        return 'Authoritative account data is being resolved.';
      case 'error':
        return 'Account context could not be resolved.';
      default:
        return 'Select an account before account-scoped trading actions.';
    }
  }

  marketValue(): string {
    switch (this.view.marketState) {
      case 'ready':
        return this.view.market?.symbol ?? this.view.market?.marketId ?? 'UNKNOWN';
      case 'loading':
        return 'Loading market';
      case 'unavailable':
      case 'error':
        return 'Unavailable';
      case 'ineligible':
        return 'Not eligible';
      case 'not-tradable':
        return 'Not tradable';
      default:
        return 'No market selected';
    }
  }

  marketDetail(): string {
    switch (this.view.marketState) {
      case 'ready':
        return `${this.view.market?.provider ?? 'Unknown provider'} · ${this.productTypeLabel(this.view.market?.productType)}`;
      case 'loading':
        return 'Market facts are being resolved after eligibility.';
      case 'unavailable':
      case 'error':
        return 'Market facts are currently unavailable.';
      case 'ineligible':
        return 'This account context excludes the selected market.';
      case 'not-tradable':
        return 'Market Data reports that this market cannot be used now.';
      default:
        return 'Choose an eligible market to inspect its live context.';
    }
  }

  tradabilityLabel(): string {
    if (this.view.marketState === 'ready' && this.view.market) {
      return this.view.market.marketState.tradable ? 'Tradable' : 'Not tradable';
    }
    if (this.view.marketState === 'ineligible') {
      return 'Not eligible';
    }
    if (this.view.marketState === 'not-tradable') {
      return 'Not tradable';
    }
    return 'UNKNOWN';
  }

  tradabilityTone(): StatusBadgeTone {
    if (this.view.marketState === 'ready' && this.view.market?.marketState.tradable) {
      return 'positive';
    }
    if (this.view.marketState === 'ineligible' || this.view.marketState === 'not-tradable') {
      return 'warning';
    }
    return 'neutral';
  }

  tradabilityDetail(): string {
    if (this.view.marketState === 'ready' && this.view.market) {
      return this.view.market.marketState.tradingStatus;
    }
    if (this.view.marketState === 'ineligible') {
      return 'Eligibility comes from the selected account context.';
    }
    if (this.view.marketState === 'not-tradable') {
      return 'Market Data reports the current market state.';
    }
    return 'Market state is not available.';
  }
}
