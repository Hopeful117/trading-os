import type { NewsAvailability } from './news-availability.model';

export type EconomicEventImpact = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
export type EconomicEventStatus = 'SCHEDULED' | 'RELEASED' | 'CANCELLED' | 'UNKNOWN';

export type { NewsAvailability } from './news-availability.model';

export interface EconomicEvent {
  id: string;
  sourceName: string;
  source: string | null;
  sourceEventId: string;
  title: string;
  category: string | null;
  scheduledAt: string;
  actualAt: string | null;
  currencies: string[];
  marketIds: string[];
  impact: EconomicEventImpact;
  status: EconomicEventStatus;
  previousValue: string | null;
  consensusValue: string | null;
  actualValue: string | null;
  unit: string | null;
  sourceUpdatedAt: string | null;
  fetchedAt: string;
  normalizationVersion: string;
}

export interface EconomicEventQuery {
  from: string;
  to: string;
  currency?: string;
  impact?: EconomicEventImpact;
  limit?: number;
}

export interface EconomicEventResponse {
  status: NewsAvailability;
  items: EconomicEvent[];
  fetchedAt: string | null;
  message: string | null;
  attribution: string | null;
}
