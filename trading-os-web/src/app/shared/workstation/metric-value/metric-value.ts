import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-metric-value',
  standalone: true,
  template: `
    <div class="metric-value">
      <span class="metric-value__label">{{ label }}</span>
      <strong [class.metric-value__number]="isNumeric">{{ displayValue }}</strong>
      @if (detail) {
        <small>{{ detail }}</small>
      }
    </div>
  `,
  styleUrl: './metric-value.scss',
})
export class MetricValue {
  @Input({ required: true }) label!: string;
  @Input() value: string | number | null | undefined;
  @Input() detail: string | null = null;

  get displayValue(): string | number {
    return this.value === null || this.value === undefined || this.value === ''
      ? 'UNKNOWN'
      : this.value;
  }

  get isNumeric(): boolean {
    return typeof this.value === 'number';
  }
}
