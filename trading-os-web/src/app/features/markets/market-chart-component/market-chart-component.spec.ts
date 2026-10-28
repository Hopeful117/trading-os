import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { MarketChartComponent } from './market-chart-component';

let chartSeries: { setData: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };

vi.mock('lightweight-charts', () => ({
  CandlestickSeries: {},
  ColorType: { Solid: 'solid' },
  createChart: () => ({
    addSeries: () => {
      chartSeries = { setData: vi.fn(), update: vi.fn() };
      return chartSeries;
    },
    remove: vi.fn(),
    resize: vi.fn(),
    timeScale: () => ({
      fitContent: vi.fn(),
      setVisibleLogicalRange: vi.fn(),
    }),
  }),
}));

describe('MarketChartComponent', () => {
  let component: MarketChartComponent;
  let fixture: ComponentFixture<MarketChartComponent>;

  beforeEach(async () => {
    chartSeries = { setData: vi.fn(), update: vi.fn() };
    globalThis.ResizeObserver = class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    };

    await TestBed.configureTestingModule({
      imports: [MarketChartComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ignores live candles older than the loaded history', async () => {
    const historyCandle = candle('2026-10-28T10:00:00Z');
    const staleLiveCandle = candle('2026-10-28T09:55:00Z');

    fixture.componentRef.setInput('history', [historyCandle]);
    fixture.detectChanges();
    await fixture.whenStable();
    chartSeries.update.mockClear();

    fixture.componentRef.setInput('liveCandle', staleLiveCandle);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(chartSeries.update).not.toHaveBeenCalled();
  });

  it('updates the current candle and accepts newer candles', async () => {
    const historyCandle = candle('2026-10-28T10:00:00Z');
    const currentLiveCandle = candle('2026-10-28T10:00:00Z');
    const newerLiveCandle = candle('2026-10-28T10:05:00Z');

    fixture.componentRef.setInput('history', [historyCandle]);
    fixture.detectChanges();
    await fixture.whenStable();
    chartSeries.update.mockClear();

    fixture.componentRef.setInput('liveCandle', currentLiveCandle);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentRef.setInput('liveCandle', newerLiveCandle);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(chartSeries.update).toHaveBeenCalledTimes(2);
  });

  function candle(openTime: string) {
    return {
      marketId: 'market-1',
      provider: 'KRAKEN',
      symbol: 'BTC/USD',
      streamType: 'OHLC' as const,
      interval: { minutes: 5, interval: '5m' } as never,
      openTime,
      closeTime: new Date(new Date(openTime).getTime() + 5 * 60_000).toISOString(),
      open: 100,
      high: 110,
      low: 90,
      close: 105,
      volume: 1,
      vwap: 100,
      trades: 1,
      closed: false,
      occurredAt: openTime,
    };
  }
});
