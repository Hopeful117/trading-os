import { ExecutionStatus } from './execution.model';

export interface TradePlanProvenance {
  opportunityId: string;
  opportunityVersion: number;
  strategyMatchId: string | null;
  strategyId: string | null;
  strategyVersion: number | null;
  accountId: string | null;
  sourceScanId: string | null;
  sourceScanMarketId: string | null;
  analysisExecutionId: string | null;
  marketId: string | null;
}

export interface DecisionHistoryItem {
  outcomeId: string;
  executionIntentId: string;
  accountId: string;
  accountName: string;
  origin: 'MANUAL' | 'OPPORTUNITY' | string;
  provenance: TradePlanProvenance[];
  tradeId: string | null;
  tradePlanId: string | null;
  tradePlanVersion: number;
  riskEvaluationId: string | null;
  instrument: string;
  side: string;
  quantity: number | null;
  plannedEntryPrice: number | null;
  stopLossPrice: number | null;
  expectedMonetaryRisk: number | null;
  riskRewardRatio: number | null;
  actualEntryPrice: number | null;
  realizedPnl: number | null;
  actualFee: number | null;
  outcomeStatus: string;
  executionStatus: ExecutionStatus | null;
  tradeStatus: string | null;
  openedAt: string | null;
  closedAt: string | null;
  lineageAvailable: boolean;
  resultAvailable: boolean;
  closeExecutionIntentId: string | null;
  closeExecutionStatus: ExecutionStatus | null;
  createdAt: string;
}
