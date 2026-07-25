import {ChangeDetectionStrategy,output, Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import{debounceTime,distinctUntilChanged,startWith} from 'rxjs';
import { MarketFilter } from '../../../core/models/market-filter.model';


@Component({
  selector: 'app-market-toolbar-component',
  imports: [ReactiveFormsModule],
  templateUrl: './market-toolbar-component.html',
  styleUrl: './market-toolbar-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MarketToolbarComponent {
  readonly filterChange = output<MarketFilter>();
  readonly refreshRequested = output<void>();

  readonly searchControl = new FormControl('', {
    nonNullable: true,
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        startWith(this.searchControl.value),
        debounceTime(250),
        distinctUntilChanged(),
      )
      .subscribe(search => {
        this.filterChange.emit({
          search: search.trim(),
        });
      });
  }

  refresh(): void {
    this.refreshRequested.emit();
  }

  clearSearch(): void {
    this.searchControl.setValue('');
  }
}
