import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { combineLatest, debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import {
  DEFAULT_MARKET_SORT,
  MarketFilter,
  MarketSort,
  MarketSortDirection,
  MarketSortField,
} from '../../../core/models/market-filter.model';

@Component({
  selector: 'app-market-toolbar-component',
  imports: [ReactiveFormsModule],
  templateUrl: './market-toolbar-component.html',
  styleUrl: './market-toolbar-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketToolbarComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly filterChange = output<MarketFilter>();
  readonly sortChange = output<MarketSort>();
  readonly refreshRequested = output<void>();
  readonly resetRequested = output<void>();

  readonly providers = input<readonly string[]>([]);
  readonly baseAssets = input<readonly string[]>([]);
  readonly quoteAssets = input<readonly string[]>([]);
  readonly statuses = input<readonly string[]>([]);

  readonly searchControl = new FormControl('', {
    nonNullable: true,
  });
  readonly providerControl = new FormControl('', { nonNullable: true });
  readonly baseAssetControl = new FormControl('', { nonNullable: true });
  readonly quoteAssetControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl('', { nonNullable: true });
  readonly tradableControl = new FormControl('', { nonNullable: true });
  readonly sortFieldControl = new FormControl<MarketSortField>(DEFAULT_MARKET_SORT.field, {
    nonNullable: true,
  });
  readonly sortDirectionControl = new FormControl<MarketSortDirection>(
    DEFAULT_MARKET_SORT.direction,
    { nonNullable: true },
  );

  constructor() {
    const search$ = this.searchControl.valueChanges.pipe(
      startWith(this.searchControl.value),
      debounceTime(250),
      distinctUntilChanged(),
    );
    const provider$ = this.providerControl.valueChanges.pipe(
      startWith(this.providerControl.value),
      distinctUntilChanged(),
    );
    const baseAsset$ = this.baseAssetControl.valueChanges.pipe(
      startWith(this.baseAssetControl.value),
      distinctUntilChanged(),
    );
    const quoteAsset$ = this.quoteAssetControl.valueChanges.pipe(
      startWith(this.quoteAssetControl.value),
      distinctUntilChanged(),
    );
    const status$ = this.statusControl.valueChanges.pipe(
      startWith(this.statusControl.value),
      distinctUntilChanged(),
    );
    const tradable$ = this.tradableControl.valueChanges.pipe(
      startWith(this.tradableControl.value),
      distinctUntilChanged(),
    );

    combineLatest([search$, provider$, baseAsset$, quoteAsset$, status$, tradable$])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([search, provider, baseAsset, quoteAsset, status, tradable]) => {
        this.filterChange.emit({
          search: search.trim(),
          ...(provider ? { provider } : {}),
          ...(baseAsset ? { baseAsset } : {}),
          ...(quoteAsset ? { quoteAsset } : {}),
          ...(status ? { tradingStatus: status } : {}),
          ...(tradable === 'true' || tradable === 'false' ? { tradable: tradable === 'true' } : {}),
        });
      });

    combineLatest([
      this.sortFieldControl.valueChanges.pipe(startWith(this.sortFieldControl.value)),
      this.sortDirectionControl.valueChanges.pipe(startWith(this.sortDirectionControl.value)),
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([field, direction]) => this.sortChange.emit({ field, direction }));
  }

  refresh(): void {
    this.refreshRequested.emit();
  }

  clearSearch(): void {
    this.searchControl.setValue('');
  }

  reset(): void {
    this.searchControl.setValue('');
    this.providerControl.setValue('');
    this.baseAssetControl.setValue('');
    this.quoteAssetControl.setValue('');
    this.statusControl.setValue('');
    this.tradableControl.setValue('');
    this.sortFieldControl.setValue(DEFAULT_MARKET_SORT.field);
    this.sortDirectionControl.setValue(DEFAULT_MARKET_SORT.direction);
    this.resetRequested.emit();
  }
}
