import { ExecutionStatus } from './execution.model';

export interface DecisionHistoryItem {
  outcomeId: string;
  executionIntentId: string;
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
  createdAt: string;
}
