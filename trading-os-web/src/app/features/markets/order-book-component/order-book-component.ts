import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';

import { OrderBookSnapshot } from '../../../core/models/order-book-snapshot.model';

@Component({
  selector: 'app-order-book',
  imports: [DecimalPipe, PercentPipe],
  templateUrl: './order-book-component.html',
  styleUrl: './order-book-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderBookComponent {
  readonly snapshot = input.required<OrderBookSnapshot>();
}
