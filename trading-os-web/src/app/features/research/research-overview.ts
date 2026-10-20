import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Observable, Subject, catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';

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
    switchMap(() =>
      this.researchService.getReadiness().pipe(
        switchMap((readiness) =>
          this.researchService.getIntegrity().pipe(
            map(
              (integrity) => ({ kind: 'ready', readiness, integrity }) as ResearchOverviewViewModel,
            ),
            startWith({ kind: 'ready', readiness, integrity: null } as ResearchOverviewViewModel),
            catchError(() =>
              of({ kind: 'ready', readiness, integrity: null } as ResearchOverviewViewModel),
            ),
          ),
        ),
        startWith({ kind: 'loading' } as ResearchOverviewViewModel),
        catchError(() =>
          of<ResearchOverviewViewModel>({
            kind: 'error',
            message: 'Research readiness is temporarily unavailable. Check the Gateway connection.',
          }),
        ),
      ),
    ),
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
