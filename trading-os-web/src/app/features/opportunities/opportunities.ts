import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, of, shareReplay, startWith, Subject, switchMap } from 'rxjs';

import { OpportunityResponse } from '../../core/models/opportunity.model';
import { OpportunityService } from '../../core/services/opportunity.service';
import { ScanPanel } from './scan-panel/scan-panel';

export type OpportunitiesView =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'loaded'; opportunities: OpportunityResponse[] };

@Component({
  selector: 'app-opportunities',
  imports: [AsyncPipe, DatePipe, DecimalPipe, ScanPanel],
  templateUrl: './opportunities.html',
  styleUrl: './opportunities.scss',
})
export class Opportunities {
  private readonly opportunityService = inject(OpportunityService);
  private readonly router = inject(Router);

  private readonly refreshSubject = new Subject<void>();
  private readonly refresh$ = this.refreshSubject.pipe(startWith(undefined));

  readonly view$ = this.refresh$.pipe(
    switchMap(() =>
      this.opportunityService.findActive().pipe(
        map((opportunities) => ({ status: 'loaded' as const, opportunities })),
        catchError(() => of({ status: 'error' as const })),
        startWith({ status: 'loading' as const }),
      ),
    ),
    shareReplay({
      bufferSize: 1,
      refCount: true,
    }),
  );

  openOpportunity(opportunityId: string): void {
    void this.router.navigate(['/opportunities', opportunityId]);
  }

  refreshOpportunities(): void {
    this.refreshSubject.next();
  }
}
