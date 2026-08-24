export type OpportunityStatus = 'DETECTED' | 'ANALYZED' | 'ACTIVE' | 'CONSUMED' | 'EXPIRED';

export type OpportunityDirection = 'LONG' | 'SHORT' | 'NEUTRAL';

export type OpportunityType = 'SCALPING' | 'INTRADAY' | 'SWING' | 'POSITIONAL';

export type OpportunityOrigin =
  'PASSIVE_SCAN' | 'ACTIVE_SCAN' | 'USER_REQUEST' | 'SYSTEM_REEVALUATION';

export interface OpportunityResponse {
  id: string;
  version: number;
  status: OpportunityStatus;
  instrument: string;
  direction: OpportunityDirection;
  scenario: string;
  timeframe: string;
  type: OpportunityType;
  origin: OpportunityOrigin;
  score: number;
  explanation: string;
  observationIds: string[];
  aiAnalysisIds: string[];
  evaluatedAt: string;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
  strategyMatchId: string | null;
}

export interface OpportunityPageResponse {
  items: OpportunityResponse[];
  page: number;
  size: number;
  total: number;
}
