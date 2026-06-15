import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subscription, catchError, switchMap, timer } from 'rxjs';
import { PublicTracking } from '../../core/api/models';
import { TrackingService } from '../../core/api/tracking.service';
import { ParcelTimeline } from '../../shared/parcel-timeline';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-tracking-page',
  imports: [
    ReactiveFormsModule,
    DatePipe,
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
              <mat-label>Трек-номер (CT-...)</mat-label>
              <input matInput data-testid="tracking-search-input" formControlName="number" placeholder="CT-XXXXXXXXXX" />
            </mat-form-field>
            <button matButton="filled" type="submit" [disabled]="form.invalid || loading()">
              <mat-icon>search</mat-icon>
              {{ loading() ? 'Ищем...' : 'Найти' }}
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
              <span class="tracking-number" data-testid="tracking-number">{{ r.trackingNumber }}</span>
              <app-status-badge [status]="r.status" />
            </mat-card-title>
            <mat-card-subtitle>
              {{ r.originCity }} -> {{ r.destinationCity }} · получатель: {{ r.recipientNameMasked }}
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p class="refresh-state" data-testid="tracking-refresh-state">
              @if (lastUpdated(); as updated) {
                Обновлено {{ updated | date: 'HH:mm:ss' }} · автообновление каждые 3 секунды
              } @else {
                Получаем актуальный статус...
              }
            </p>
            @if (r.tracking; as tracking) {
              <div class="public-progress">
                <div class="progress-copy">
                  <span>Прогресс маршрута</span>
                  <strong>{{ tracking.progressPercent }}%</strong>
                </div>
                <div class="progress-track" aria-hidden="true">
                  <span [style.width.%]="tracking.progressPercent"></span>
                </div>
                @if (tracking.updatedAt) {
                  <p>Обновлено {{ tracking.updatedAt | date: 'dd.MM.yyyy HH:mm' }}</p>
                }
              </div>
            }
            <app-parcel-timeline [events]="r.events" />
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .tracking-page { max-width: 760px; margin: 0 auto; display: grid; gap: 1rem; }
    .search-form { display: flex; gap: 0.75rem; align-items: center; }
    .search-field { flex: 1; }
    .search-error { color: var(--mat-sys-error, #b3261e); margin: 0.75rem 0 0; }
    .result-title { display: flex; align-items: center; gap: 0.75rem; }
    .tracking-number { font-family: monospace; }
    .refresh-state { margin: 0 0 0.75rem; opacity: .7; font-size: .9rem; }
    .public-progress { display: grid; gap: .45rem; margin-bottom: 1rem; }
    .progress-copy { display: flex; justify-content: space-between; gap: 1rem; }
    .progress-track { height: 8px; overflow: hidden; border-radius: 999px; background: var(--mat-sys-surface-container-high); }
    .progress-track span { display: block; height: 100%; border-radius: inherit; background: var(--mat-sys-primary); }
    .public-progress p { margin: 0; opacity: .7; font-size: .85rem; }
    app-parcel-timeline { display: block; margin-top: 1rem; }
    @media (max-width: 640px) {
      .search-form { align-items: stretch; flex-direction: column; }
    }
  `,
})
export class TrackingPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly tracking = inject(TrackingService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private pollSubscription?: Subscription;

  protected readonly result = signal<PublicTracking | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly lastUpdated = signal<Date | null>(null);

  protected readonly form = this.fb.group({
    number: ['', Validators.required],
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.pollSubscription?.unsubscribe());
    const fromRoute = inject(ActivatedRoute).snapshot.paramMap.get('number');
    if (fromRoute) {
      this.form.patchValue({ number: fromRoute });
      queueMicrotask(() => this.search());
    }
  }

  protected search(): void {
    const number = this.normalizeTrackingNumber(this.form.getRawValue().number);
    if (!number || this.loading()) {
      return;
    }
    this.form.patchValue({ number }, { emitEvent: false });
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.lastUpdated.set(null);
    this.router.navigate(['/track', number], { replaceUrl: true });
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = timer(0, 3000)
      .pipe(
        switchMap(() =>
          this.tracking.track(number).pipe(
            catchError((err) => {
              this.loading.set(false);
              this.error.set(this.errorMessage(err, true));
              if (err instanceof HttpErrorResponse && err.status === 404) {
                this.pollSubscription?.unsubscribe();
              }
              return EMPTY;
            }),
          ),
        ),
      )
      .subscribe((result) => {
        this.loading.set(false);
        this.error.set(null);
        this.result.set(result);
        this.lastUpdated.set(new Date());
      });
  }

  private errorMessage(err: unknown, autoRefreshContinues = false): string {
    if (err instanceof HttpErrorResponse && err.status === 404) {
      return 'Посылка с таким номером не найдена. Проверьте номер.';
    }
    return autoRefreshContinues
      ? 'Не удалось выполнить поиск. Автообновление продолжает попытки.'
      : 'Не удалось выполнить поиск. Попробуйте позже.';
  }

  private normalizeTrackingNumber(value: string): string {
    return value.trim().toUpperCase();
  }
}
