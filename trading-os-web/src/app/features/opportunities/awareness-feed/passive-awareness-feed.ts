import { AsyncPipe } from '@angular/common';
import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import {
  AwarenessFeedView,
  mapToFeedView,
  PassiveAwarenessQueryParams,
} from '../../../core/models/passive-awareness.model';
import { PassiveAwarenessService } from '../../../core/services/passive-awareness.service';
import { PassiveCandidateCard } from './passive-candidate-card/passive-candidate-card';
import { PassiveCandidateInvestigatePanel } from './passive-candidate-investigate-panel/passive-candidate-investigate-panel';
import { PassiveAwarenessItem } from '../../../core/models/passive-awareness.model';

@Component({
  selector: 'app-passive-awareness-feed',
  imports: [AsyncPipe, FormsModule, PassiveCandidateCard, PassiveCandidateInvestigatePanel],
  templateUrl: './passive-awareness-feed.html',
  styleUrl: './passive-awareness-feed.scss',
})
export class PassiveAwarenessFeed implements OnInit {
  private readonly awarenessService = inject(PassiveAwarenessService);

  currentOnly = signal(false);
  instrumentFilter = signal('');
  currentPage = signal(0);

  investigatingCandidate = signal<PassiveAwarenessItem | null>(null);

  private readonly refreshSubject = new Subject<void>();
  private readonly instrumentSubject = new Subject<string>();

  readonly view$: Observable<AwarenessFeedView> = this.refreshSubject.pipe(
    startWith(undefined),
    switchMap(() =>
      this.awarenessService
        .query({
          currentOnly: this.currentOnly(),
          instrument: this.instrumentFilter() || undefined,
          page: this.currentPage(),
          size: 20,
        })
        .pipe(
          switchMap((r) =>
            of(
              mapToFeedView(r, {
                currentOnly: this.currentOnly(),
                instrument: this.instrumentFilter() || undefined,
              }),
            ),
          ),
          catchError((err: { status?: number; error?: { code?: string } }) =>
            of({
              status: 'error' as const,
              httpStatus: err.status ?? 0,
              code: err.error?.code,
            }),
          ),
          startWith({ status: 'loading' as const }),
        ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  ngOnInit(): void {
    this.instrumentSubject
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((value) => {
        this.instrumentFilter.set(value);
        this.currentPage.set(0);
        this.refreshSubject.next();
      });
  }

  refresh(): void {
    this.refreshSubject.next();
  }

  setCurrentOnly(value: boolean): void {
    this.currentOnly.set(value);
    this.currentPage.set(0);
    this.refreshSubject.next();
  }

  onInstrumentInput(value: string): void {
    this.instrumentSubject.next(value);
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
    this.refreshSubject.next();
  }

  openInvestigation(item: PassiveAwarenessItem): void {
    this.investigatingCandidate.set(item);
  }

  closeInvestigation(): void {
    this.investigatingCandidate.set(null);
  }

  pageEnd(pagination: { page: number; size: number; total: number }): number {
    return Math.min((pagination.page + 1) * pagination.size, pagination.total);
  }
}
