import { AccountBalance } from './account-balance.model';

export interface Account {
  accountId: string;
  name: string;
  baseCurrency: string;
  balances: AccountBalance;
  equity: number;
  peakEquity: number;
  rulesId: string;
  userId: string;
}
