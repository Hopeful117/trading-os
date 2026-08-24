import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { vi } from 'vitest';

import { MarketToolbarComponent } from './market-toolbar-component';

describe('MarketToolbarComponent', () => {
  let component: MarketToolbarComponent;
  let fixture: ComponentFixture<MarketToolbarComponent>;

  beforeEach(async () => {
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [MarketToolbarComponent, ReactiveFormsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketToolbarComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('searchControl', () => {
    it('should emit filterChange on value change after debounce', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('BTC');
      vi.advanceTimersByTime(250);

      expect(emissions.length).toBeGreaterThanOrEqual(1);
      expect(emissions[emissions.length - 1]).toEqual({ search: 'BTC' });
    });

    it('should debounce with 250ms delay', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('B');
      vi.advanceTimersByTime(100);
      expect(emissions.length).toBe(0);

      component.searchControl.setValue('BT');
      vi.advanceTimersByTime(100);
      expect(emissions.length).toBe(0);

      component.searchControl.setValue('BTC');
      vi.advanceTimersByTime(250);
      expect(emissions.length).toBeGreaterThanOrEqual(1);
      expect(emissions[emissions.length - 1]).toEqual({ search: 'BTC' });
    });

    it('should not re-emit same value (distinctUntilChanged)', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('BTC');
      vi.advanceTimersByTime(250);
      const countAfterFirst = emissions.length;

      component.searchControl.setValue('X');
      vi.advanceTimersByTime(250);
      component.searchControl.setValue('BTC');
      vi.advanceTimersByTime(250);

      expect(emissions.length).toBe(countAfterFirst + 2);
    });

    it('should trim whitespace from emitted search', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('  ETH  ');
      vi.advanceTimersByTime(250);

      expect(emissions[emissions.length - 1]).toEqual({ search: 'ETH' });
    });

    it('should emit initial empty value on subscription', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      vi.advanceTimersByTime(250);

      expect(emissions.length).toBeGreaterThanOrEqual(1);
      expect(emissions[0]).toEqual({ search: '' });
    });
  });

  describe('refresh()', () => {
    it('should emit refreshRequested event', () => {
      const spy = vi.fn();
      component.refreshRequested.subscribe(spy);

      component.refresh();

      expect(spy).toHaveBeenCalledOnce();
    });

    it('should emit refreshRequested on each call', () => {
      const emissions: any[] = [];
      component.refreshRequested.subscribe(() => emissions.push(undefined));

      component.refresh();
      component.refresh();
      component.refresh();

      expect(emissions.length).toBe(3);
    });
  });

  describe('clearSearch()', () => {
    it('should reset searchControl to empty string', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('BTC');
      vi.advanceTimersByTime(250);
      expect(component.searchControl.value).toBe('BTC');

      component.clearSearch();
      vi.advanceTimersByTime(250);

      expect(component.searchControl.value).toBe('');
      expect(emissions[emissions.length - 1]).toEqual({ search: '' });
    });

    it('should emit filterChange with empty search after clear', () => {
      const emissions: any[] = [];
      component.filterChange.subscribe((f) => emissions.push(f));

      component.searchControl.setValue('ETH');
      vi.advanceTimersByTime(250);
      emissions.length = 0;

      component.clearSearch();
      vi.advanceTimersByTime(250);

      expect(emissions[0]).toEqual({ search: '' });
    });
  });
});
