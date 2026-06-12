import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { STATUS_LABELS, TrackingEvent } from '../core/api/models';

/** Таймлайн событий посылки (SDP, раздел 6.1, уровень 3). */
@Component({
  selector: 'app-parcel-timeline',
  imports: [DatePipe, MatIconModule],
  template: `
    <ol class="timeline">
      @for (event of events(); track event.createdAt; let last = $last) {
        <li class="timeline-item" [class.timeline-item-last]="last">
          <span class="timeline-dot">
            <mat-icon inline>{{ last ? 'radio_button_checked' : 'check_circle' }}</mat-icon>
          </span>
          <div class="timeline-body">
            <strong>{{ labels[event.status] }}</strong>
            @if (event.description) {
              <div class="timeline-description">{{ event.description }}</div>
            }
            <div class="timeline-meta">
              {{ event.createdAt | date: 'dd.MM.yyyy HH:mm' }}
              @if (event.warehouseCity) {
                · {{ event.warehouseCity }}
              }
            </div>
          </div>
        </li>
      } @empty {
        <li>Событий пока нет.</li>
      }
    </ol>
  `,
  styles: `
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .timeline-item {
      position: relative;
      display: flex;
      gap: 0.75rem;
      padding-bottom: 1.25rem;
    }
    .timeline-item:not(.timeline-item-last)::before {
      content: '';
      position: absolute;
      left: 0.55rem;
      top: 1.4rem;
      bottom: 0;
      width: 2px;
      background: var(--mat-sys-outline-variant, #ccc);
    }
    .timeline-dot {
      color: var(--mat-sys-primary, #3f51b5);
      z-index: 1;
    }
    .timeline-description {
      font-size: 0.9rem;
    }
    .timeline-meta {
      font-size: 0.8rem;
      opacity: 0.7;
    }
  `,
})
export class ParcelTimeline {
  readonly events = input.required<TrackingEvent[]>();

  protected readonly labels = STATUS_LABELS;
}
