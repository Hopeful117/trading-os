import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { MarketChartComponent, shouldApplyLiveCandle } from './market-chart-component';

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

  it('rejects live candles older than the latest chart candle', () => {
    expect(shouldApplyLiveCandle(1_000, 999)).toBe(false);
    expect(shouldApplyLiveCandle(1_000, 1_000)).toBe(true);
  });

  it('accepts the first and newer live candles', () => {
    expect(shouldApplyLiveCandle(null, 1_000)).toBe(true);
    expect(shouldApplyLiveCandle(1_000, 1_001)).toBe(true);
  });
});
