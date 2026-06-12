import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { ParcelDetail } from '../../core/api/models';
import { ParcelsService } from '../../core/api/parcels.service';
import { ParcelTimeline } from '../../shared/parcel-timeline';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-parcel-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    StatusBadge,
    ParcelTimeline,
  ],
  template: `
    @if (detail(); as d) {
      <div class="detail-header">
        <a matIconButton routerLink="/parcels" aria-label="Назад">
          <mat-icon>arrow_back</mat-icon>
        </a>
        <h2 class="tracking-number">{{ d.parcel.trackingNumber }}</h2>
        <app-status-badge [status]="d.parcel.status" />
        <span class="spacer"></span>
        @if (d.parcel.status === 'CREATED') {
          <button matButton color="warn" (click)="cancel()" [disabled]="cancelling()">
            Отменить
          </button>
        }
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Детали</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <dl class="props">
              <dt>Маршрут</dt>
              <dd>
                {{ d.parcel.originWarehouse.city }} ({{ d.parcel.originWarehouse.name }}) →
                {{ d.parcel.destinationWarehouse.city }} ({{ d.parcel.destinationWarehouse.name }})
              </dd>
              <dt>Получатель</dt>
              <dd>{{ d.parcel.recipientName }}, {{ d.parcel.recipientPhone }}</dd>
              <dt>Вес</dt>
              <dd>{{ d.parcel.weightKg | number: '1.0-2' }} кг</dd>
              @if (d.parcel.lengthCm) {
                <dt>Габариты</dt>
                <dd>{{ d.parcel.lengthCm }} × {{ d.parcel.widthCm }} × {{ d.parcel.heightCm }} см</dd>
              }
              <dt>Цена</dt>
              <dd>{{ d.parcel.price | number: '1.2-2' }} €</dd>
              <dt>Создана</dt>
              <dd>{{ d.parcel.createdAt | date: 'dd.MM.yyyy HH:mm' }}</dd>
            </dl>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header>
            <mat-card-title>История</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <app-parcel-timeline [events]="d.events" />
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <p>Загрузка…</p>
    }
  `,
  styles: `
    .detail-header { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .tracking-number { font-family: monospace; margin: 0; }
    .spacer { flex: 1 1 auto; }
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }
    .props { display: grid; grid-template-columns: auto 1fr; gap: 0.4rem 1rem; margin: 0; }
    .props dt { font-weight: 500; opacity: 0.7; }
    .props dd { margin: 0; }
  `,
})
export class ParcelDetailPage {
  /** Берётся из роута благодаря withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly parcels = inject(ParcelsService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly detail = signal<ParcelDetail | null>(null);
  protected readonly cancelling = signal(false);

  constructor() {
    inject(MatDialog); // прогреваем DI для будущих диалогов
    queueMicrotask(() => this.load());
  }

  protected cancel(): void {
    if (!confirm('Отменить посылку? Это действие необратимо.')) {
      return;
    }
    this.cancelling.set(true);
    this.parcels.cancel(Number(this.id())).subscribe({
      next: () => {
        this.snackBar.open('Посылка отменена', 'OK', { duration: 5000 });
        this.cancelling.set(false);
        this.load();
      },
      error: (err) => {
        this.cancelling.set(false);
        this.snackBar.open(err?.error?.detail ?? 'Не удалось отменить', 'OK', { duration: 5000 });
      },
    });
  }

  private load(): void {
    this.parcels.detail(Number(this.id())).subscribe((detail) => this.detail.set(detail));
  }
}
