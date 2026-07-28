import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { MarketChartComponent } from './market-chart-component';

vi.mock('lightweight-charts', () => ({
  CandlestickSeries: {},
  ColorType: { Solid: 'solid' },
  createChart: () => ({
    addSeries: () => ({
      setData: vi.fn(),
      update: vi.fn(),
    }),
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
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
