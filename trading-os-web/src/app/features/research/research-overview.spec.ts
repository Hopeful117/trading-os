// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ResearchOverview } from './research-overview';

describe('ResearchOverview', () => {
  let fixture: ComponentFixture<ResearchOverview>;
  let httpMock: HttpTestingController;

  const response = {
    enabled: true,
    configuredMarkets: ['BTC-USD', 'ETH-USD'],
    observationCadence: 'PT1H',
    outcomeHorizons: ['PT1H', 'PT4H'],
    featureEvidencePolicy: 'STRICT_OBSERVED',
    latestSuccessfulEvidenceKnownAt: {},
    featureSnapshotCount: 60,
    featureSnapshotsByMarket: { 'BTC-USD': 30, 'ETH-USD': 30 },
    earliestFeatureSnapshotCutoff: '2026-10-01T00:00:00Z',
    latestFeatureSnapshotCutoff: '2026-10-15T00:00:00Z',
    outcomeCountsByHorizonAndStatus: {},
    eligibleOutcomeCount: 60,
    pendingOutcomeCount: 0,
    approximateAvailableResearchPopulation: 60,
    s1: {
      totalSignals: 60,
      longSignals: 30,
      shortSignals: 30,
      aggregateRegimes: ['TRENDING', 'RANGING'],
      replayChecks: 60,
      replayAvailable: 60,
      replayCoverage: 1,
      feeChecks: 60,
      feeAvailable: 60,
      feeCoverage: 1,
      ready: true,
      blockingReasons: [],
    },
  };

  const integrityResponse = {
    evaluatedAt: '2026-10-16T10:00:00Z',
    enabled: true,
    configuredMarkets: ['BTC-USD', 'ETH-USD'],
    observationCadence: 'PT1H',
    status: 'HEALTHY',
    acquisitionByMarket: {},
    maturationByHorizon: {},
    overdueOutcomeCount: 0,
    latestSuccessfulOutcomeComputedAt: '2026-10-16T09:00:00Z',
    timestampAnomalyCount: 0,
    warnings: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResearchOverview],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ResearchOverview);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
      )
      .flush(response);

    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
      )
      .flush(integrityResponse);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify({ ignoreCancelled: true }));

  it('renders readiness without inventing experiment results', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('S1 ready');
    expect(text).toContain('30 LONG / 30 SHORT');
    expect(text).toContain('Results unavailable by design');
    expect(text).toContain('Deferred: returns');
    expect(text).toContain('HEALTHY');
  });

  it('renders blocking reasons from the authoritative readiness response', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
      )
      .flush({
        ...response,
        s1: { ...response.s1, ready: false, blockingReasons: ['fee_coverage_incomplete'] },
      });
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
      )
      .flush(integrityResponse);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('S1 blocked');
    expect(fixture.nativeElement.textContent).toContain('fee coverage incomplete');
  });

  it('projects degraded integrity warnings without changing readiness semantics', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
      )
      .flush(response);
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
      )
      .flush({
        ...integrityResponse,
        status: 'DEGRADED',
        overdueOutcomeCount: 2,
        timestampAnomalyCount: 1,
        warnings: ['overdue_outcome', 'outcome_timestamp_incoherent'],
      });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('S1 ready');
    expect(text).toContain('DEGRADED');
    expect(text).toContain('overdue outcome');
    expect(text).toContain('timestamp incoherent');
  });

  it('renders a retryable error state', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
      )
      .flush('unavailable', { status: 503, statusText: 'Unavailable' });
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
      )
      .flush(integrityResponse);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Readiness unavailable');
    expect(fixture.nativeElement.textContent).toContain('Try again');
  });

  it('shows loading and sends one request when refreshed', () => {
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading readiness');

    const readinessRequests = httpMock.match(
      (candidate) =>
        candidate.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
    );
    const integrityRequests = httpMock.match(
      (candidate) =>
        candidate.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
    );
    expect(readinessRequests).toHaveLength(1);
    expect(integrityRequests).toHaveLength(1);
    readinessRequests[0].flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('S1 ready');
    expect(fixture.nativeElement.textContent).toContain('Acquisition integrity is unknown');
    integrityRequests[0].flush(integrityResponse);
  });

  it('starts integrity before readiness completes without delaying readiness rendering', () => {
    fixture.componentInstance.refresh();
    fixture.detectChanges();

    const readinessRequest = httpMock.expectOne(
      (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
    );
    const integrityRequest = httpMock.expectOne(
      (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
    );

    integrityRequest.flush(integrityResponse);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading readiness');

    readinessRequest.flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('S1 ready');
    expect(fixture.nativeElement.textContent).toContain('HEALTHY');
  });

  it('keeps readiness visible when the integrity projection is unavailable', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/readiness`,
      )
      .flush(response);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('S1 ready');
    expect(fixture.nativeElement.textContent).toContain('Acquisition integrity is unknown');

    httpMock
      .expectOne(
        (request) => request.url === `${environment.gatewayUrl}v1/intelligence/research/integrity`,
      )
      .flush('unavailable', { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('S1 ready');
    expect(fixture.nativeElement.textContent).toContain('Acquisition integrity is unknown');
  });
});
