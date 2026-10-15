import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';

import {
  EconomicEvent,
  EconomicEventImpact,
  EconomicEventQuery,
  EconomicEventResponse,
} from '../../core/models/economic-event.model';
import { NewsService } from '../../core/services/news.service';

interface CalendarFilters {
  from: string;
  to: string;
  currency: string;
  impact: EconomicEventImpact | '';
}

type EconomicContextViewModel =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'unavailable'; response: EconomicEventResponse }
  | { kind: 'ready'; response: EconomicEventResponse; events: EconomicEvent[] };

@Component({
  selector: 'app-economic-context',
  imports: [AsyncPipe, DatePipe, FormsModule],
  templateUrl: './economic-context.html',
  styleUrl: './economic-context.scss',
})
export class EconomicContext {
  private readonly newsService = inject(NewsService);
  private readonly filtersSubject = new BehaviorSubject<CalendarFilters>(this.defaultFilters());
  private readonly refreshSubject = new BehaviorSubject<void>(undefined);

  readonly filters$ = this.filtersSubject.asObservable();
  readonly impacts: EconomicEventImpact[] = ['HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];
  readonly viewModel$: Observable<EconomicContextViewModel> = combineLatest([
    this.filters$,
    this.refreshSubject,
  ]).pipe(
    switchMap(([filters]) => {
      return this.newsService.findEconomicEvents(this.toQuery(filters)).pipe(
        map((response) => this.toViewModel(response)),
        startWith({ kind: 'loading' } as EconomicContextViewModel),
        catchError(() =>
          of<EconomicContextViewModel>({
            kind: 'error',
            message:
              'The economic context could not be loaded. Check the Gateway connection and try again.',
          }),
        ),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  updateFrom(from: string): void {
    this.updateFilters({ from });
  }

  updateTo(to: string): void {
    this.updateFilters({ to });
  }

  updateCurrency(currency: string): void {
    this.updateFilters({ currency: currency.toUpperCase() });
  }

  updateImpact(impact: string): void {
    this.updateFilters({ impact: impact as EconomicEventImpact | '' });
  }

  refresh(): void {
    this.refreshSubject.next();
  }

  trackEvent(_: number, event: EconomicEvent): string {
    return event.id;
  }

  private updateFilters(change: Partial<CalendarFilters>): void {
    this.filtersSubject.next({ ...this.filtersSubject.value, ...change });
  }

  private toQuery(filters: CalendarFilters): EconomicEventQuery {
    return {
      from: `${filters.from}T00:00:00.000Z`,
      to: `${filters.to}T23:59:59.999Z`,
      currency: filters.currency || undefined,
      impact: filters.impact || undefined,
      limit: 100,
    };
  }

  private toViewModel(response: EconomicEventResponse): EconomicContextViewModel {
    if (
      response.status !== 'AVAILABLE' &&
      !(response.status === 'STALE' && response.items.length > 0)
    ) {
      return { kind: 'unavailable', response };
    }

    return {
      kind: 'ready',
      response,
      events: [...response.items].sort(
        (left, right) => Date.parse(left.scheduledAt) - Date.parse(right.scheduledAt),
      ),
    };
  }

  private defaultFilters(): CalendarFilters {
    const today = new Date();
    const end = new Date(today);
    end.setUTCDate(end.getUTCDate() + 7);

    return {
      from: this.toDateInput(today),
      to: this.toDateInput(end),
      currency: '',
      impact: '',
    };
  }

  private toDateInput(date: Date): string {
    return [
      date.getUTCFullYear(),
      String(date.getUTCMonth() + 1).padStart(2, '0'),
      String(date.getUTCDate()).padStart(2, '0'),
    ].join('-');
  }
}
