import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  effect,
  inject,
  input,
} from '@angular/core';

import {
  CandlestickData,
  CandlestickSeries,
  ColorType,
  createChart,
  IChartApi,
  ISeriesApi,
  Time,
} from 'lightweight-charts';

import { OhlcEvent } from '../../../core/models/ohlc-event.model'
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-market-chart',
  templateUrl: './market-chart-component.html',
  styleUrl: './market-chart-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AsyncPipe],
})
export class MarketChartComponent implements AfterViewInit {
  @ViewChild('chartContainer', { static: true })
  private readonly chartContainer!: ElementRef<HTMLDivElement>;

  readonly candle = input<OhlcEvent | null>(null);

  private readonly destroyRef = inject(DestroyRef);

  private chart: IChartApi | null = null;

  private candlestickSeries: ISeriesApi<'Candlestick'> | null = null;

  private resizeObserver: ResizeObserver | null = null;

  readonly resetKey = input<number>(0);

  constructor() {
    effect(() => {
      /*
       * La lecture enregistre la dépendance.
       */
      this.resetKey();

      if (!this.candlestickSeries) {
        return;
      }

      this.candlestickSeries.setData([]);

      this.chart?.timeScale().setVisibleLogicalRange({
        from: -40,
        to: 5,
      });
    });

    effect(() => {
      const candle = this.candle();

      if (candle === null || this.candlestickSeries === null) {
        return;
      }

      this.candlestickSeries.update(this.toCandlestickData(candle));
    });
  }

  ngAfterViewInit(): void {
    this.createChart();
    this.observeContainerSize();

    this.destroyRef.onDestroy(() => {
      this.resizeObserver?.disconnect();
      this.chart?.remove();

      this.resizeObserver = null;
      this.candlestickSeries = null;
      this.chart = null;
    });
  }

  private createChart(): void {
    const container = this.chartContainer.nativeElement;
    this.chart = createChart(container, {
      width: container.clientWidth,
      height: container.clientHeight || 440,

      layout: {
        background: {
          type: ColorType.Solid,
          color: '#0f172a',
        },
        textColor: '#94a3b8',
      },

      grid: {
        vertLines: {
          color: 'rgba(148, 163, 184, 0.08)',
        },
        horzLines: {
          color: 'rgba(148, 163, 184, 0.08)',
        },
      },

      rightPriceScale: {
        borderColor: 'rgba(148, 163, 184, 0.16)',
        scaleMargins: {
          top: 0.15,
          bottom: 0.15,
        },
      },

      timeScale: {
        borderColor: 'rgba(148, 163, 184, 0.16)',
        timeVisible: true,
        secondsVisible: false,
        barSpacing: 8,
        minBarSpacing: 4,
        rightOffset: 4,
        fixLeftEdge: false,
        fixRightEdge: false,
      },
    });

    this.candlestickSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#22c55e',
      downColor: '#ef4444',
      wickUpColor: '#22c55e',
      wickDownColor: '#ef4444',
      borderVisible: false,
    });

    const initialCandle = this.candle();

    if (initialCandle) {
      this.candlestickSeries.update(this.toCandlestickData(initialCandle));

      this.chart.timeScale().fitContent();
    }
  }

  private observeContainerSize(): void {
    const container = this.chartContainer.nativeElement;

    this.resizeObserver = new ResizeObserver((entries) => {
      const entry = entries.at(0);

      if (!entry || !this.chart) {
        return;
      }

      const { width, height } = entry.contentRect;

      this.chart.resize(width, height || 440);
    });

    this.resizeObserver.observe(container);
  }

  private toCandlestickData(candle: OhlcEvent): CandlestickData<Time> {
    console.log('[CHART] OHLC received', candle);

    const data = {
      time: Math.floor(new Date(candle.openTime).getTime() / 1000) as Time,
      open: Number(candle.open),
      high: Number(candle.high),
      low: Number(candle.low),
      close: Number(candle.close),
    };

    console.log('[CHART] Candlestick data', data);

    return data;
  }

  private toUnixTimestamp(instant: string): Time {
    return Math.floor(new Date(instant).getTime() / 1000) as Time;
  }
}
