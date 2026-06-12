import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { PublicTracking } from '../../core/api/models';
import { TrackingService } from '../../core/api/tracking.service';
import { ParcelTimeline } from '../../shared/parcel-timeline';
import { StatusBadge } from '../../shared/status-badge';

/** Публичный трекинг по номеру — работает без логина (SDP, 7.2). */
@Component({
  selector: 'app-tracking-page',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    StatusBadge,
    ParcelTimeline,
  ],
  template: `
    <div class="tracking-page">
      <mat-card class="search-card">
        <mat-card-header>
          <mat-card-title>Отследить посылку</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form class="search-form" [formGroup]="form" (ngSubmit)="search()">
            <mat-form-field appearance="outline" class="search-field" subscriptSizing="dynamic">
              <mat-label>Трек-номер (CT-…)</mat-label>
              <input matInput formControlName="number" placeholder="CT-XXXXXXXXXX" />
            </mat-form-field>
            <button matButton="filled" type="submit" [disabled]="form.invalid || loading()">
              <mat-icon>search</mat-icon>
              {{ loading() ? 'Ищем…' : 'Найти' }}
            </button>
          </form>
          @if (error()) {
            <p class="search-error" role="alert">{{ error() }}</p>
          }
        </mat-card-content>
      </mat-card>

      @if (result(); as r) {
        <mat-card class="result-card">
          <mat-card-header>
            <mat-card-title class="result-title">
              <span class="tracking-number">{{ r.trackingNumber }}</span>
              <app-status-badge [status]="r.status" />
            </mat-card-title>
            <mat-card-subtitle>
              {{ r.originCity }} → {{ r.destinationCity }} · получатель: {{ r.recipientNameMasked }}
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <app-parcel-timeline [events]="r.events" />
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .tracking-page { max-width: 640px; margin: 0 auto; display: grid; gap: 1rem; }
    .search-form { display: flex; gap: 0.75rem; align-items: center; }
    .search-field { flex: 1; }
    .search-error { color: var(--mat-sys-error, #b3261e); margin: 0.75rem 0 0; }
    .result-title { display: flex; align-items: center; gap: 0.75rem; }
    .tracking-number { font-family: monospace; }
  `,
})
export class TrackingPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly tracking = inject(TrackingService);
  private readonly router = inject(Router);

  protected readonly result = signal<PublicTracking | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly form = this.fb.group({
    number: ['', Validators.required],
  });

  constructor() {
    // прямая ссылка /track/CT-XXXX — ищем сразу
    const fromRoute = inject(ActivatedRoute).snapshot.paramMap.get('number');
    if (fromRoute) {
      this.form.patchValue({ number: fromRoute });
      queueMicrotask(() => this.search());
    }
  }

  protected search(): void {
    const number = this.form.getRawValue().number.trim();
    if (!number || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.router.navigate(['/track', number], { replaceUrl: true });
    this.tracking.track(number).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.result.set(result);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err?.status === 404
            ? 'Посылка с таким номером не найдена. Проверьте номер.'
            : 'Не удалось выполнить поиск. Попробуйте позже.',
        );
      },
    });
  }
}
