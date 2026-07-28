import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { RecentTradesSnapshot } from '../../../core/models/recent-trades-snapshot.model';

@Component({
  selector: 'app-recent-trades',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './recent-trades-component.html',
  styleUrl: './recent-trades-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecentTradesComponent {
  readonly snapshot = input.required<RecentTradesSnapshot>();
}
