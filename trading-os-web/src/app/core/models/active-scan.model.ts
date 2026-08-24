import { OpportunityResponse } from './opportunity.model';

export type ActiveScanStatus =
  | 'READY_TO_DISPATCH'
  | 'DISPATCH_REQUESTED'
  | 'RUNNING'
  | 'PARTIALLY_COMPLETED'
  | 'COMPLETED'
  | 'FAILED'
  | 'COMPLETED_NO_WORK';

export const ACTIVE_SCAN_TERMINAL_STATUSES: readonly ActiveScanStatus[] = [
  'PARTIALLY_COMPLETED',
  'COMPLETED',
  'FAILED',
  'COMPLETED_NO_WORK',
];

export function isActiveScanTerminal(status: ActiveScanStatus): boolean {
  return ACTIVE_SCAN_TERMINAL_STATUSES.includes(status);
}

export type AnalysisExecutionStatus =
  | 'REQUESTED'
  | 'ACCEPTED'
  | 'CONTEXT_BUILDING'
  | 'RUNNING'
  | 'PARTIALLY_COMPLETED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type MarketEligibilityReason = 'MARKET_NOT_FOUND' | 'MARKET_NOT_TRADABLE';

export interface CreateActiveScanRequest {
  accountId: string;
  objective?: string;
  requestedMarketIds?: string[];
}

export interface ActiveScanProgress {
  totalCandidates: number;
  eligible: number;
  excluded: number;
  running: number;
  completed: number;
  failed: number;
  opportunitiesFound: number;
}

export interface ScanDiagnostic {
  code: string;
  message: string;
}

export interface StrategyProvenance {
  strategyMatchId: string;
  strategyId: string;
  strategyVersion: number;
}

export interface ActiveScanMarketResult {
  scanMarketId: string;
  ordinal: number;
  marketId: string;
  eligible: boolean;
  analysisStatus: AnalysisExecutionStatus;
  resultQuality: 'COMPLETE' | 'PARTIAL' | 'DEGRADED' | null;
  outcome:
    | 'EXCLUDED'
    | 'RUNNING'
    | 'COMPLETED_NO_OPPORTUNITY'
    | 'OPPORTUNITY_FOUND'
    | 'FAILED'
    | 'CANCELLED'
    | 'EXPIRED'
    | null;
  analysisExecutionId: string | null;
  exclusionReasons: MarketEligibilityReason[];
  diagnostic: ScanDiagnostic | null;
  opportunity: OpportunityResponse | null;
  strategy: StrategyProvenance | null;
}

export interface ActiveScanResponse {
  scanId: string;
  accountId: string;
  objective: string | null;
  status: ActiveScanStatus;
  requestedMarketIds: string[] | null;
  candidateMarketIds: string[];
  effectiveMarketIds: string[];
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  progress: ActiveScanProgress;
  markets: ActiveScanMarketResult[];
}
