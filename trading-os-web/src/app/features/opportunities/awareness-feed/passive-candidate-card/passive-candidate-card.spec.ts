// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PassiveCandidateCard } from './passive-candidate-card';
import { PassiveAwarenessItem } from '../../../../core/models/passive-awareness.model';

function makeItem(overrides: Partial<PassiveAwarenessItem> = {}): PassiveAwarenessItem {
  return {
    opportunityId: 'opp-1',
    opportunityVersion: 1,
    current: true,
    diagnostics: [],
    lifecycleState: 'ACTIVE',
    presentationState: 'PASSIVE_CANDIDATE',
    instrument: 'EURUSD',
    direction: 'LONG',
    scenario: 'TREND_FOLLOWING',
    timeframe: '1H',
    origin: 'PASSIVE_SCAN',
    marketId: 'market-1',
    provider: 'IC Markets',
    productType: 'CFD',
    strategyMatchId: 'match-1',
    observationIds: [],
    observationTimes: [],
    thesisId: 'thesis-1',
    thesisVersion: 1,
    sourceScanId: 'scan-1',
    sourceScanMarketId: 'scan-market-1',
    analysisExecutionId: 'exec-1',
    evaluatedAt: '2026-11-14T10:00:00Z',
    sourceCutoff: null,
    validFrom: '2026-11-14T10:00:00Z',
    validUntil: null,
    createdAt: '2026-11-14T10:00:00Z',
    isCandidate: true,
    isExpired: false,
    isSuperseded: false,
    freshnessSummary: '2 min ago',
    diagnosticCategory: null,
    diagnosticLabels: [],
    ...overrides,
  };
}

describe('PassiveCandidateCard', () => {
  let fixture: ComponentFixture<PassiveCandidateCard>;
  let component: PassiveCandidateCard;

  function create(item: PassiveAwarenessItem) {
    fixture = TestBed.createComponent(PassiveCandidateCard);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('item', item);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PassiveCandidateCard],
    }).compileComponents();
  });

  it('renders instrument and direction', () => {
    create(makeItem());
    expect(fixture.nativeElement.querySelector('[data-testid="card-instrument"]').textContent.trim()).toBe('EURUSD');
    expect(fixture.nativeElement.querySelector('[data-testid="card-direction"]').textContent.trim()).toBe('LONG');
  });

  it('renders provider', () => {
    create(makeItem());
    expect(fixture.nativeElement.querySelector('[data-testid="card-provider"]').textContent.trim()).toBe('IC Markets');
  });

  it('renders provider unavailable when provider is null', () => {
    create(makeItem({ provider: null }));
    expect(fixture.nativeElement.querySelector('[data-testid="card-provider-unavailable"]')).not.toBeNull();
  });

  it('renders freshness summary', () => {
    create(makeItem());
    expect(fixture.nativeElement.querySelector('[data-testid="card-freshness"]').textContent).toContain('2 min ago');
  });

  it('shows Investigate button for candidates', () => {
    create(makeItem({ isCandidate: true, isExpired: false, isSuperseded: false }));
    const btn = fixture.nativeElement.querySelector('[data-testid="investigate-button"]');
    expect(btn).not.toBeNull();
    expect(btn.disabled).toBe(false);
  });

  it('disables Investigate button for expired candidates', () => {
    create(makeItem({ isCandidate: true, isExpired: true }));
    const btn = fixture.nativeElement.querySelector('[data-testid="investigate-button"]');
    expect(btn.disabled).toBe(true);
  });

  it('disables Investigate button for superseded candidates', () => {
    create(makeItem({ isCandidate: true, isSuperseded: true }));
    const btn = fixture.nativeElement.querySelector('[data-testid="investigate-button"]');
    expect(btn.disabled).toBe(true);
  });

  it('does not show Investigate button for non-positive items', () => {
    create(makeItem({ isCandidate: false, presentationState: 'NON_POSITIVE' }));
    expect(fixture.nativeElement.querySelector('[data-testid="investigate-button"]')).toBeNull();
  });

  it('shows diagnostics section for non-positive items with diagnostics', () => {
    create(makeItem({
      isCandidate: false,
      presentationState: 'NON_POSITIVE',
      diagnostics: ['OPPORTUNITY_SUPERSEDED', 'THESIS_SUPERSEDED'],
    }));
    expect(fixture.nativeElement.querySelector('[data-testid="card-diagnostics"]')).not.toBeNull();
  });

  it('emits investigateClicked when Investigate is clicked', () => {
    const item = makeItem();
    create(item);
    const emitted: PassiveAwarenessItem[] = [];
    component.investigateClicked.subscribe((v) => emitted.push(v));
    fixture.nativeElement.querySelector('[data-testid="investigate-button"]').click();
    expect(emitted).toHaveLength(1);
    expect(emitted[0].opportunityId).toBe('opp-1');
  });

  it('shows expired badge for expired items', () => {
    create(makeItem({ isExpired: true }));
    expect(fixture.nativeElement.querySelector('[data-testid="expired-badge"]')).not.toBeNull();
  });

  it('shows superseded badge for superseded items', () => {
    create(makeItem({ isSuperseded: true }));
    expect(fixture.nativeElement.querySelector('[data-testid="superseded-badge"]')).not.toBeNull();
  });
});
