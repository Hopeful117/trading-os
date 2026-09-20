import { AccountBalance } from './account-balance.model';

export interface Account {
  accountId: string;
  brokerAccountId?: string | null;
  name: string;
  baseCurrency: string;
  balances: AccountBalance;
  equity: number;
  peakEquity: number;
  rulesId: string;
  userId: string;
  riskProfileId?: string | null;
  riskProfileSemanticVersion?: string | null;
  tradePlanningProfileId?: string | null;
  tradePlanningProfileVersion?: number | null;
}
