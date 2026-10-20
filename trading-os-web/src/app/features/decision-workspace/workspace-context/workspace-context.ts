import { DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';

import { DecisionContextAccount } from '../../../core/models/decision-context.model';
import { MarketResponse, marketProductTypeLabel } from '../../../core/models/market-response';
import { OpportunityResponse } from '../../../core/models/opportunity.model';

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
  selector: 'app-workspace-context',
  imports: [DatePipe],
  standalone: true,
  templateUrl: './workspace-context.html',
  styleUrl: './workspace-context.scss',
})
export class WorkspaceContext {
  @Input({ required: true }) view!: WorkspaceContextView;

  readonly productTypeLabel = marketProductTypeLabel;
}
