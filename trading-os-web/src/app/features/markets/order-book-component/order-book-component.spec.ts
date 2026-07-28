import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OrderBookSnapshot } from '../../../core/models/order-book-snapshot.model';
import { OrderBookComponent } from './order-book-component';

describe('OrderBookComponent', () => {
  let fixture: ComponentFixture<OrderBookComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderBookComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderBookComponent);
    fixture.componentRef.setInput('snapshot', snapshot);
    fixture.detectChanges();
  });

  it('renders the complete backend snapshot without rebuilding it', () => {
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('Asks');
    expect(content).toContain('Bids');
    expect(content).toContain('100.5');
    expect(content).toContain('100');
    expect(content).toContain('0.5');
    expect(content).toContain('60.0%');
  });
});

const snapshot: OrderBookSnapshot = {
  marketId: '3ff91f78-dc74-4327-bb81-daa7df3fbc11',
  provider: 'KRAKEN',
  symbol: 'BTC/EUR',
  streamType: 'ORDER_BOOK',
  depth: 10,
  bids: [{ price: 100, quantity: 3 }],
  asks: [{ price: 100.5, quantity: 2 }],
  bestBid: 100,
  bestAsk: 100.5,
  spread: 0.5,
  bidVolume: 3,
  askVolume: 2,
  imbalance: 0.6,
  occurredAt: '2026-07-28T12:00:00Z',
};
