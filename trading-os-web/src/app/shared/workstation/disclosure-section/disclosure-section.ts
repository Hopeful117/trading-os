import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-disclosure-section',
  standalone: true,
  template: `
    <section class="disclosure-section">
      <button
        type="button"
        class="disclosure-button disclosure-section__button"
        [attr.aria-expanded]="expanded"
        (click)="expandedChange.emit(!expanded)"
      >
        <span>
          @if (eyebrow) {
            <span class="disclosure-section__eyebrow">{{ eyebrow }}</span>
          }
          <strong>{{ title }}</strong>
        </span>
        <span class="disclosure-section__summary">
          {{ summary }}
          <span aria-hidden="true">{{ expanded ? '−' : '+' }}</span>
        </span>
      </button>
      @if (expanded) {
        <div class="disclosure-section__content">
          <ng-content />
        </div>
      }
    </section>
  `,
  styleUrl: './disclosure-section.scss',
})
export class DisclosureSection {
  @Input({ required: true }) title!: string;
  @Input() eyebrow: string | null = null;
  @Input() summary: string | null = null;
  @Input() expanded = false;
  @Output() readonly expandedChange = new EventEmitter<boolean>();
}
