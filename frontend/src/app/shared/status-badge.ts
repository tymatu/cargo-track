import { Component, computed, input } from '@angular/core';
import { ParcelStatus, STATUS_LABELS } from '../core/api/models';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge ' + cssClass()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 1rem;
      font-size: 0.8rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .badge-created { background: #e3f2fd; color: #1565c0; }
    .badge-progress { background: #fff8e1; color: #b26a00; }
    .badge-transit { background: #ede7f6; color: #5e35b1; }
    .badge-done { background: #e8f5e9; color: #2e7d32; }
    .badge-cancelled { background: #fbe9e7; color: #c62828; }
  `,
})
export class StatusBadge {
  readonly status = input.required<ParcelStatus>();

  protected readonly label = computed(() => STATUS_LABELS[this.status()]);

  protected readonly cssClass = computed(() => {
    switch (this.status()) {
      case 'CREATED':
        return 'badge-created';
      case 'ACCEPTED_AT_ORIGIN':
      case 'LOADED':
        return 'badge-progress';
      case 'IN_TRANSIT':
      case 'ARRIVED_AT_DESTINATION':
        return 'badge-transit';
      case 'DELIVERED':
        return 'badge-done';
      case 'CANCELLED':
        return 'badge-cancelled';
    }
  });
}
