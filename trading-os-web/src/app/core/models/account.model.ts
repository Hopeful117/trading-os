import { AccountBalance } from './account-balance.model';

export interface Account {
  accountId: string;
  brokerAccountId?: string | null;
  name: string;
  baseCurrency: string;
  balances: AccountBalance;
  equity: number | null;
  peakEquity: number | null;
  rulesId: string;
  userId: string;
  synchronizationStatus?: string | null;
  financialDataStatus?: string | null;
  riskEvidenceStatus?: string | null;
  tradingReadinessStatus?: string | null;
  snapshotCompleteness?: string | null;
  snapshotFreshness?: string | null;
  positionAvailability?: string | null;
  orderAvailability?: string | null;
  pnlAvailability?: string | null;
  dealAvailability?: string | null;
  synchronizationReasons?: string[];
  derivedEquityCandidate?: number | null;
  derivedEquityCurrency?: string | null;
  derivedEquityProvenance?: string | null;
  derivedEquityCalculationVersion?: string | null;
  derivedEquityHypothesis?: string | null;
  derivedEquityConsistencyStatus?: string | null;
  derivedEquityValidationStatus?: string | null;
  derivedEquityReasons?: string[];
  diagnosticOnly?: boolean;
  riskEvidenceIncomplete?: boolean;
  tradingNotAuthorized?: boolean;
  riskProfileId?: string | null;
  riskProfileSemanticVersion?: string | null;
  tradePlanningProfileId?: string | null;
  tradePlanningProfileVersion?: number | null;
}

export interface BrokerAccountSynchronizationResult {
  brokerAccountId: string;
  status: string;
  observedAt: string;
  completedAt: string;
  completeness: string;
  freshness: string;
  positionAvailability: string;
  orderAvailability: string;
  pnlAvailability: string;
  dealAvailability: string;
  persistedCapabilities: string[];
  unavailabilityReasons: string[];
  diagnosticOnly: boolean;
}
