// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { EconomicContext } from './economic-context';

describe('EconomicContext', () => {
  let fixture: ComponentFixture<EconomicContext>;
  let httpMock: HttpTestingController;

  const response = {
    status: 'AVAILABLE',
    fetchedAt: '2026-10-15T12:00:00Z',
    message: null,
    attribution: 'Calendar source',
    items: [
      {
        id: 'later',
        sourceName: 'XOOMAR',
        source: 'Calendar',
        sourceEventId: '2',
        title: 'Later event',
        category: 'Macro',
        scheduledAt: '2026-10-16T14:30:00Z',
        actualAt: null,
        currencies: ['USD'],
        marketIds: [],
        impact: 'MEDIUM',
        status: 'SCHEDULED',
        previousValue: '3.0%',
        consensusValue: '3.1%',
        actualValue: null,
        unit: '%',
        sourceUpdatedAt: null,
        fetchedAt: '2026-10-15T12:00:00Z',
        normalizationVersion: 'v1',
      },
      {
        id: 'first',
        sourceName: 'XOOMAR',
        source: 'Calendar',
        sourceEventId: '1',
        title: 'First event',
        category: 'Macro',
        scheduledAt: '2026-10-15T09:00:00Z',
        actualAt: '2026-10-15T09:00:00Z',
        currencies: ['USD'],
        marketIds: [],
        impact: 'HIGH',
        status: 'RELEASED',
        previousValue: '2.0%',
        consensusValue: '2.2%',
        actualValue: '2.4%',
        unit: '%',
        sourceUpdatedAt: null,
        fetchedAt: '2026-10-15T12:00:00Z',
        normalizationVersion: 'v1',
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EconomicContext],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(EconomicContext);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/events`)
      .flush(response);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('renders events chronologically with normalized values and attribution', () => {
    const text = fixture.nativeElement.textContent;
    expect(text.indexOf('First event')).toBeLessThan(text.indexOf('Later event'));
    expect(text).toContain('HIGH');
    expect(text).toContain('Actual');
    expect(text).toContain('2.4%');
    expect(text).toContain('Forecast');
    expect(text).toContain('Calendar source');
  });

  it('sends the selected filters when changed', () => {
    fixture.componentInstance.updateCurrency('eur');
    const request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`,
    );
    expect(request.request.params.get('currency')).toBe('EUR');
    request.flush({ ...response, items: [] });
  });

  it('sends date and importance filters through the API', () => {
    fixture.componentInstance.updateFrom('2026-10-20');
    let request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`,
    );
    expect(request.request.params.get('from')).toBe('2026-10-20T00:00:00.000Z');
    request.flush({ ...response, items: [] });

    fixture.componentInstance.updateImpact('HIGH');
    request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`,
    );
    expect(request.request.params.get('impact')).toBe('HIGH');
    request.flush({ ...response, items: [] });
  });

  it('shows loading while a refresh request is pending', () => {
    fixture.componentInstance.refresh();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading economic context');
    httpMock
      .expectOne((candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`)
      .flush(response);
  });

  it('renders an empty available response separately from unavailable context', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`)
      .flush({ ...response, items: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No economic events in this window');
    expect(fixture.nativeElement.textContent).not.toContain('Economic context is unavailable');
  });

  it('keeps stale events visible with an explicit freshness warning', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`)
      .flush({ ...response, status: 'STALE', message: 'Provider refresh is stale' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Calendar data may be stale');
    expect(fixture.nativeElement.textContent).toContain('First event');
  });

  it('renders request errors with a retry state', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`)
      .flush('Gateway unavailable', { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Context unavailable');
    expect(fixture.nativeElement.textContent).toContain('Try again');
  });

  it('does not invent absent event values', () => {
    const cards = fixture.nativeElement.querySelectorAll('.event-card');
    expect(cards[1].textContent).toContain('Previous');
    expect(cards[1].textContent).toContain('Forecast');
    expect(cards[1].textContent).not.toContain('Actual');
  });

  it('renders unavailable context distinctly from an empty calendar', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((candidate) => candidate.url === `${environment.gatewayUrl}v1/news/events`)
      .flush({
        status: 'UNAVAILABLE',
        items: [],
        fetchedAt: null,
        message: 'Provider unavailable',
        attribution: null,
      });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Economic context is unavailable');
    expect(fixture.nativeElement.textContent).not.toContain('No economic events in this window');
  });
});
