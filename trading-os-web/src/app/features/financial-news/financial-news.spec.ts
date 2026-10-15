// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { FinancialNewsResponse } from '../../core/models/financial-news.model';
import { FinancialNews } from './financial-news';

describe('FinancialNews', () => {
  let fixture: ComponentFixture<FinancialNews>;
  let httpMock: HttpTestingController;

  const response: FinancialNewsResponse = {
    status: 'AVAILABLE',
    fetchedAt: '2026-10-15T12:00:00Z',
    message: null,
    attribution: null,
    items: [
      {
        id: 'news-1',
        sourceName: 'fixture',
        sourceItemId: 'article-1',
        title: 'Bitcoin reacts to latest macroeconomic data',
        summary: 'Digital asset markets moved following the latest release.',
        canonicalUrl: 'https://example.test/article-1',
        publishedAt: '2026-10-15T11:30:00Z',
        publisher: 'Example Markets',
        categories: ['macro'],
        currencies: ['BTC', 'USD'],
        marketIds: ['11111111-1111-1111-1111-111111111111'],
        impact: 'UNKNOWN',
        sourceUpdatedAt: null,
        fetchedAt: '2026-10-15T12:00:00Z',
        normalizationVersion: 'fixture-v1',
      },
      {
        id: 'news-2',
        sourceName: 'fixture',
        sourceItemId: 'article-2',
        title: 'Article without optional context',
        summary: null,
        canonicalUrl: null,
        publishedAt: '2026-10-15T10:30:00Z',
        publisher: null,
        categories: [],
        currencies: [],
        marketIds: [],
        impact: 'UNKNOWN',
        sourceUpdatedAt: null,
        fetchedAt: '2026-10-15T12:00:00Z',
        normalizationVersion: 'fixture-v1',
      },
    ],
  };

  beforeEach(async () => {
    vi.setSystemTime(new Date('2026-10-15T12:00:00Z'));

    await TestBed.configureTestingModule({
      imports: [FinancialNews],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(FinancialNews);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush(response);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('renders normalized article facts and a safe source link', () => {
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Bitcoin reacts to latest macroeconomic data');
    expect(text).toContain('Example Markets');
    expect(text).toContain('Digital asset markets moved following the latest release.');
    expect(text).toContain('macro');
    expect(text).toContain('BTC, USD');
    expect(text).toContain('Markets: 11111111-1111-1111-1111-111111111111');

    const link = fixture.nativeElement.querySelector('a.source-link');
    expect(link.getAttribute('href')).toBe('https://example.test/article-1');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('does not invent absent optional article fields', () => {
    const cards = fixture.nativeElement.querySelectorAll('.news-card');
    expect(cards[1].textContent).toContain('Article without optional context');
    expect(cards[1].querySelector('.summary')).toBeNull();
    expect(cards[1].querySelector('.source-link')).toBeNull();
  });

  it('maps the selected recent window and currency to the API request', () => {
    fixture.componentInstance.updateWindow('24h');
    let request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/items`,
    );
    expect(request.request.params.get('from')).toBe('2026-10-14T12:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-10-15T12:00:00.000Z');
    expect(request.request.params.get('limit')).toBe('100');
    request.flush(response);

    fixture.componentInstance.updateCurrency('btc');
    request = httpMock.expectOne(
      (candidate) => candidate.url === `${environment.gatewayUrl}v1/news/items`,
    );
    expect(request.request.params.get('currency')).toBe('BTC');
    request.flush(response);
  });

  it('shows loading while a refresh request is pending', () => {
    fixture.componentInstance.refresh();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading financial news');
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush(response);
  });

  it('distinguishes an available empty catalog from provider unavailability', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush({ ...response, items: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No financial news matches this window');

    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush({ ...response, status: 'UNAVAILABLE', items: [], message: 'Catalog unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Financial news is unavailable');
    expect(fixture.nativeElement.textContent).not.toContain(
      'No financial news matches this window',
    );
  });

  it('renders unsupported provider state without exposing backend details', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush({
        ...response,
        status: 'UNSUPPORTED',
        message: 'The configured news provider is unsupported',
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Financial news is unsupported');
    expect(fixture.nativeElement.textContent).not.toContain(
      'Bitcoin reacts to latest macroeconomic data',
    );
    expect(fixture.nativeElement.textContent).not.toContain(
      'The configured news provider is unsupported',
    );
    expect(fixture.nativeElement.textContent).not.toContain('api_token');
  });

  it('keeps stale and incomplete articles visible with explicit warnings', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush({ ...response, status: 'STALE', message: 'Catalog is stale' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('News data may be stale.');
    expect(fixture.nativeElement.textContent).toContain(
      'Bitcoin reacts to latest macroeconomic data',
    );

    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush({ ...response, status: 'INCOMPLETE' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('News data is incomplete.');
  });

  it('renders a retryable state for HTTP failures', () => {
    fixture.componentInstance.refresh();
    httpMock
      .expectOne((request) => request.url === `${environment.gatewayUrl}v1/news/items`)
      .flush('Gateway unavailable', { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Financial news unavailable');
    expect(fixture.nativeElement.textContent).toContain('Try again');

    fixture.nativeElement.querySelector('.state-error button').click();
    const retryRequest = httpMock.expectOne(
      (request) => request.url === `${environment.gatewayUrl}v1/news/items`,
    );
    retryRequest.flush(response);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Bitcoin reacts to latest macroeconomic data',
    );
  });
});
