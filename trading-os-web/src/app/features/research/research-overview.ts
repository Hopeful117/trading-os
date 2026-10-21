import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import {
  Observable,
  Subject,
  catchError,
  map,
  merge,
  of,
  scan,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';

import {
  ResearchIntegrity,
  ResearchReadiness,
  S1Readiness,
} from '../../core/models/research-readiness.model';
import { ResearchService } from '../../core/services/research.service';

type ResearchOverviewViewModel =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; readiness: ResearchReadiness; integrity: ResearchIntegrity | null };

type ResearchLoadEvent =
  | { kind: 'readiness'; readiness: ResearchReadiness }
  | { kind: 'readiness-error' }
  | { kind: 'integrity'; integrity: ResearchIntegrity }
  | { kind: 'integrity-error' };

type ResearchLoadState = {
  readiness: ResearchReadiness | null;
  readinessError: boolean;
  integrity: ResearchIntegrity | null;
};

@Component({
  selector: 'app-research-overview',
  imports: [AsyncPipe, DatePipe, DecimalPipe],
  templateUrl: './research-overview.html',
  styleUrl: './research-overview.scss',
})
export class ResearchOverview {
  private readonly researchService = inject(ResearchService);
  private readonly refreshSubject = new Subject<void>();

  readonly viewModel$: Observable<ResearchOverviewViewModel> = this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() => {
      const readiness$ = this.researchService.getReadiness().pipe(
        map((readiness): ResearchLoadEvent => ({ kind: 'readiness', readiness })),
        catchError(() => of<ResearchLoadEvent>({ kind: 'readiness-error' })),
      );
      const integrity$ = this.researchService.getIntegrity().pipe(
        map((integrity): ResearchLoadEvent => ({ kind: 'integrity', integrity })),
        catchError(() => of<ResearchLoadEvent>({ kind: 'integrity-error' })),
      );

      return merge(readiness$, integrity$).pipe(
        scan<ResearchLoadEvent, ResearchLoadState>(
          (state, event) =>
            event.kind === 'readiness'
              ? { ...state, readiness: event.readiness }
              : event.kind === 'readiness-error'
                ? { ...state, readinessError: true }
                : event.kind === 'integrity'
                  ? { ...state, integrity: event.integrity }
                  : state,
          { readiness: null, readinessError: false, integrity: null },
        ),
        map<ResearchLoadState, ResearchOverviewViewModel>((state) =>
          state.readinessError
            ? {
                kind: 'error',
                message:
                  'Research readiness is temporarily unavailable. Check the Gateway connection.',
              }
            : state.readiness
              ? { kind: 'ready', readiness: state.readiness, integrity: state.integrity }
              : { kind: 'loading' },
        ),
        startWith({ kind: 'loading' } as ResearchOverviewViewModel),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  refresh(): void {
    this.refreshSubject.next();
  }

  readinessClass(s1: S1Readiness): string {
    return s1.ready ? 'ready' : 'blocked';
  }

  coverageClass(coverage: number): string {
    return coverage >= 1 ? 'ready' : coverage > 0 ? 'partial' : 'blocked';
  }

  coveragePercent(coverage: number): number {
    return coverage * 100;
  }

  blockerLabel(reason: string): string {
    return reason.replaceAll('_', ' ');
  }
}
