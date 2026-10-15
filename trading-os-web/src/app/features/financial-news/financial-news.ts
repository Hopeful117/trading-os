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
  FinancialNewsItem,
  FinancialNewsQuery,
  FinancialNewsResponse,
} from '../../core/models/financial-news.model';
import { NewsService } from '../../core/services/news.service';

type NewsWindow = '24h' | '48h' | '7d';

const NEWS_WINDOW_HOURS: Record<NewsWindow, number> = {
  '24h': 24,
  '48h': 48,
  '7d': 24 * 7,
};

interface FinancialNewsFilters {
  window: NewsWindow;
  currency: string;
}

type FinancialNewsViewModel =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'unavailable'; response: FinancialNewsResponse }
  | { kind: 'incomplete'; response: FinancialNewsResponse }
  | { kind: 'empty'; response: FinancialNewsResponse }
  | { kind: 'ready'; response: FinancialNewsResponse; items: FinancialNewsItem[] };

@Component({
  selector: 'app-financial-news',
  imports: [AsyncPipe, DatePipe, FormsModule],
  templateUrl: './financial-news.html',
  styleUrl: './financial-news.scss',
})
export class FinancialNews {
  private readonly newsService = inject(NewsService);
  private readonly filtersSubject = new BehaviorSubject<FinancialNewsFilters>(
    this.defaultFilters(),
  );
  private readonly refreshSubject = new BehaviorSubject<void>(undefined);

  readonly filters$ = this.filtersSubject.asObservable();
  readonly windows: { value: NewsWindow; label: string }[] = [
    { value: '24h', label: 'Last 24 hours' },
    { value: '48h', label: 'Last 48 hours' },
    { value: '7d', label: 'Last 7 days' },
  ];
  readonly viewModel$: Observable<FinancialNewsViewModel> = combineLatest([
    this.filters$,
    this.refreshSubject,
  ]).pipe(
    switchMap(([filters]) =>
      this.newsService.findFinancialNews(this.toQuery(filters)).pipe(
        map((response) => this.toViewModel(response)),
        startWith({ kind: 'loading' } as FinancialNewsViewModel),
        catchError(() =>
          of<FinancialNewsViewModel>({
            kind: 'error',
            message:
              'Financial news could not be loaded. Check the Gateway connection and try again.',
          }),
        ),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  updateWindow(window: NewsWindow): void {
    this.updateFilters({ window });
  }

  updateCurrency(currency: string): void {
    this.updateFilters({ currency: currency.toUpperCase() });
  }

  refresh(): void {
    this.refreshSubject.next();
  }

  trackItem(_: number, item: FinancialNewsItem): string {
    return item.id;
  }

  private updateFilters(change: Partial<FinancialNewsFilters>): void {
    this.filtersSubject.next({ ...this.filtersSubject.value, ...change });
  }

  private toQuery(filters: FinancialNewsFilters): FinancialNewsQuery {
    const to = new Date();
    const from = new Date(to);
    from.setTime(to.getTime() - NEWS_WINDOW_HOURS[filters.window] * 60 * 60 * 1000);

    return {
      from: from.toISOString(),
      to: to.toISOString(),
      currency: filters.currency || undefined,
      limit: 100,
    };
  }

  private toViewModel(response: FinancialNewsResponse): FinancialNewsViewModel {
    if (response.status === 'AVAILABLE' && response.items.length === 0) {
      return { kind: 'empty', response };
    }

    if (
      response.items.length > 0 &&
      (response.status === 'AVAILABLE' ||
        response.status === 'STALE' ||
        response.status === 'INCOMPLETE')
    ) {
      return { kind: 'ready', response, items: response.items };
    }

    if (response.status === 'INCOMPLETE') {
      return { kind: 'incomplete', response };
    }

    return { kind: 'unavailable', response };
  }

  availabilityMessage(response: FinancialNewsResponse): string {
    switch (response.status) {
      case 'UNSUPPORTED':
        return 'Financial news is not currently supported.';
      case 'INCOMPLETE':
        return 'Some normalized financial news may be missing.';
      case 'STALE':
        return 'Review publication and fetch times before relying on this context.';
      case 'UNAVAILABLE':
        return 'The normalized financial news catalog is not currently available.';
      default:
        return 'The normalized financial news catalog is not currently available.';
    }
  }

  private defaultFilters(): FinancialNewsFilters {
    return { window: '48h', currency: '' };
  }
}
