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
  signal,
} from '@angular/core';

import {
  CandlestickData,
  CandlestickSeries,
  ColorType,
  createChart,
  IChartApi,
  ISeriesApi,
  Time,
  UTCTimestamp,
} from 'lightweight-charts';

import { OhlcEvent } from '../../../core/models/ohlc-event.model';

@Component({
  selector: 'app-market-chart',
  templateUrl: './market-chart-component.html',
  styleUrl: './market-chart-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketChartComponent implements AfterViewInit {
  @ViewChild('chartContainer', { static: true })
  private readonly chartContainer!: ElementRef<HTMLDivElement>;

  readonly history = input<OhlcEvent[]>([]);
  readonly liveCandle = input<OhlcEvent | null>(null);
  readonly resetKey = input<number>(0);

  private readonly destroyRef = inject(DestroyRef);
  private readonly chartReady = signal(false);

  private chart: IChartApi | null = null;

  private candlestickSeries: ISeriesApi<'Candlestick', Time> | null = null;

  private resizeObserver: ResizeObserver | null = null;

  constructor() {
    this.registerResetEffect();
    this.registerHistoryEffect();
    this.registerLiveCandleEffect();

    this.destroyRef.onDestroy(() => {
      this.resizeObserver?.disconnect();
      this.chart?.remove();

      this.resizeObserver = null;
      this.candlestickSeries = null;
      this.chart = null;
    });
  }

  ngAfterViewInit(): void {
    this.createChart();
    this.observeContainerSize();
    this.chartReady.set(true);
  }

  private registerResetEffect(): void {
    effect(() => {
      this.resetKey();

      if (!this.chartReady() || this.candlestickSeries === null) {
        return;
      }

      this.candlestickSeries.setData([]);

      this.chart?.timeScale().setVisibleLogicalRange({
        from: -40,
        to: 5,
      });
    });
  }

  private registerHistoryEffect(): void {
    effect(() => {
      const history = this.history();

      if (!this.chartReady() || this.candlestickSeries === null) {
        return;
      }

      if (history.length === 0) {
        return;
      }

      const candles = this.toHistoricalCandlestickData(history);

      this.candlestickSeries.setData(candles);
      this.chart?.timeScale().fitContent();
    });
  }

  private registerLiveCandleEffect(): void {
    effect(() => {
      const candle = this.liveCandle();

      if (!this.chartReady() || this.candlestickSeries === null || candle === null) {
        return;
      }

      this.candlestickSeries.update(this.toCandlestickData(candle));
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
          top: 0.12,
          bottom: 0.12,
        },
      },

      timeScale: {
        borderColor: 'rgba(148, 163, 184, 0.16)',

        timeVisible: true,
        secondsVisible: false,

        barSpacing: 8,
        minBarSpacing: 4,
        rightOffset: 4,
      },

      crosshair: {
        vertLine: {
          labelBackgroundColor: '#1e293b',
        },
        horzLine: {
          labelBackgroundColor: '#1e293b',
        },
      },
    });

    this.candlestickSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#22c55e',
      downColor: '#ef4444',

      wickUpColor: '#22c55e',
      wickDownColor: '#ef4444',

      borderVisible: false,

      priceFormat: {
        type: 'price',
        precision: 8,
        minMove: 0.00000001,
      },
    });
  }

  private observeContainerSize(): void {
    const container = this.chartContainer.nativeElement;

    this.resizeObserver = new ResizeObserver((entries) => {
      const entry = entries.at(0);

      if (entry === undefined || this.chart === null) {
        return;
      }

      const width = entry.contentRect.width;
      const height = entry.contentRect.height || 440;

      this.chart.resize(width, height);
    });

    this.resizeObserver.observe(container);
  }

  private toHistoricalCandlestickData(history: OhlcEvent[]): CandlestickData<Time>[] {
    const candlesByTimestamp = new Map<number, CandlestickData<Time>>();

    history.forEach((candle) => {
      const data = this.toCandlestickData(candle);

      candlesByTimestamp.set(Number(data.time), data);
    });

    return Array.from(candlesByTimestamp.values()).sort(
      (first, second) => Number(first.time) - Number(second.time),
    );
  }

  private toCandlestickData(candle: OhlcEvent): CandlestickData<Time> {
    const open = Number(candle.open);
    const high = Number(candle.high);
    const low = Number(candle.low);
    const close = Number(candle.close);

    this.validatePrices(candle, open, high, low, close);

    return {
      time: this.toUnixTimestamp(candle.openTime),
      open,
      high,
      low,
      close,
    };
  }

  private validatePrices(
    candle: OhlcEvent,
    open: number,
    high: number,
    low: number,
    close: number,
  ): void {
    const values = [open, high, low, close];

    if (values.some((value) => !Number.isFinite(value))) {
      throw new Error(`Invalid OHLC values for ${candle.symbol} at ${candle.openTime}`);
    }

    if (high < Math.max(open, close) || low > Math.min(open, close) || low > high) {
      throw new Error(`Inconsistent OHLC values for ${candle.symbol} at ${candle.openTime}`);
    }
  }

  private toUnixTimestamp(instant: string): UTCTimestamp {
    const milliseconds = new Date(instant).getTime();

    if (!Number.isFinite(milliseconds)) {
      throw new Error(`Invalid OHLC timestamp: ${instant}`);
    }

    return Math.floor(milliseconds / 1000) as UTCTimestamp;
  }
}
