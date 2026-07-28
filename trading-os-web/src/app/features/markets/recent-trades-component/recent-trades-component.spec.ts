import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecentTradesSnapshot } from '../../../core/models/recent-trades-snapshot.model';
import { RecentTradesComponent } from './recent-trades-component';

describe('RecentTradesComponent', () => {
  let fixture: ComponentFixture<RecentTradesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecentTradesComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RecentTradesComponent);
    fixture.componentRef.setInput('snapshot', snapshot);
    fixture.detectChanges();
  });

  it('renders the ordered backend snapshot without accumulating trades', () => {
    const content = fixture.nativeElement.textContent as string;
    const rows = fixture.nativeElement.querySelectorAll('.trade-row');

    expect(rows).toHaveLength(2);
    expect(content).toContain('BUY');
    expect(content).toContain('SELL');
    expect(content).toContain('253.125');
    expect(content).toContain('297');
  });
});

const snapshot: RecentTradesSnapshot = {
  marketId: 'cc49249d-5f49-430a-881f-f7382cb86392',
  provider: 'KRAKEN',
  symbol: 'BTC/EUR',
  trades: [
    {
      marketId: 'cc49249d-5f49-430a-881f-f7382cb86392',
      provider: 'KRAKEN',
      symbol: 'BTC/EUR',
      streamType: 'TRADES',
      tradeId: '42',
      side: 'BUY',
      price: 101.25,
      quantity: 2.5,
      notional: 253.125,
      occurredAt: '2026-07-28T12:00:01Z',
    },
    {
      marketId: 'cc49249d-5f49-430a-881f-f7382cb86392',
      provider: 'KRAKEN',
      symbol: 'BTC/EUR',
      streamType: 'TRADES',
      tradeId: '41',
      side: 'SELL',
      price: 99,
      quantity: 3,
      notional: 297,
      occurredAt: '2026-07-28T12:00:00Z',
    },
  ],
  generatedAt: '2026-07-28T12:00:01Z',
};
