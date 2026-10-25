import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
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
  PositionIntelligenceView,
  PositionScenario,
} from '../../../../core/models/position-intelligence.model';
import { PositionService } from '../../../../core/services/position.service';
import {
  FreshnessIndicator,
  FreshnessIndicatorValue,
} from '../../../../shared/workstation/freshness-indicator/freshness-indicator';
import { MetricValue } from '../../../../shared/workstation/metric-value/metric-value';
import {
  StatusBadge,
  StatusBadgeTone,
} from '../../../../shared/workstation/status-badge/status-badge';

type PositionIntelligencePageView =
  | { status: 'loading' }
  | { status: 'ready'; intelligence: PositionIntelligenceView }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-position-intelligence',
  imports: [AsyncPipe, DatePipe, RouterLink, FreshnessIndicator, MetricValue, StatusBadge],
  templateUrl: './position-intelligence.html',
  styleUrl: './position-intelligence.scss',
})
export class PositionIntelligence {
  private readonly route = inject(ActivatedRoute);
  private readonly positionService = inject(PositionService);

  readonly viewModel$: Observable<PositionIntelligencePageView> = combineLatest([
    this.route.queryParamMap,
  ]).pipe(
    switchMap(([query]) => {
      const accountId = query.get('accountId');
      const positionId = query.get('positionId');
      if (!accountId || !positionId) {
        return of<PositionIntelligencePageView>({
          status: 'error',
          message: 'Le contexte de compte ou de position est indisponible.',
        });
      }

      return this.positionService.getPositionIntelligence(accountId, positionId).pipe(
        map((intelligence) => ({ status: 'ready' as const, intelligence })),
        catchError(() =>
          of<PositionIntelligencePageView>({
            status: 'error',
            message: 'Les informations de position sont temporairement indisponibles.',
          }),
        ),
        startWith<PositionIntelligencePageView>({ status: 'loading' }),
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  scenarioValue(value: number | null, currency: string | null): string {
    return value === null ? 'Indisponible' : `${value.toFixed(2)} ${currency ?? ''}`.trim();
  }

  scenarioClass(scenario: PositionScenario): string {
    return scenario.availability.toLowerCase();
  }

  sourceLabel(source: string): string {
    return source === 'TRADING_CORE' ? 'PAPER · Trading Core' : 'LIVE · Broker Service';
  }

  freshness(positionStatus: string): FreshnessIndicatorValue {
    if (positionStatus === 'FRESH') return 'LIVE';
    if (positionStatus === 'STALE') return 'STALE';
    return 'UNAVAILABLE';
  }

  statusTone(status: string): StatusBadgeTone {
    return status === 'AVAILABLE' || status === 'FRESH' ? 'positive' : 'warning';
  }
}
