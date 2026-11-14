import { SlicePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { PassiveAwarenessItem } from '../../../../core/models/passive-awareness.model';
import { StatusBadge } from '../../../../shared/workstation/status-badge/status-badge';

@Component({
  selector: 'app-passive-candidate-card',
  imports: [SlicePipe, StatusBadge],
  templateUrl: './passive-candidate-card.html',
  styleUrl: './passive-candidate-card.scss',
})
export class PassiveCandidateCard {
  item = input.required<PassiveAwarenessItem>();
  investigating = input(false);
  investigateClicked = output<PassiveAwarenessItem>();

  get canInvestigate(): boolean {
    const i = this.item();
    return i.isCandidate && !i.isExpired && !i.isSuperseded;
  }

  get investigateDisabledReason(): string | null {
    const i = this.item();
    if (!i.isCandidate) return null;
    if (i.isSuperseded) return 'This candidate has been superseded';
    if (i.isExpired) return 'This candidate has expired';
    return null;
  }

  onInvestigate(): void {
    if (this.canInvestigate) {
      this.investigateClicked.emit(this.item());
    }
  }
}
