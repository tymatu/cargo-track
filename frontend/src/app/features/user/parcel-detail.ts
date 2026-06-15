import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  ParcelDetail,
  ParcelLiveUpdate,
  TruckPosition,
} from '../../core/api/models';
import { ParcelsService } from '../../core/api/parcels.service';
import { LiveConnectionState, LiveUpdatesService } from '../../core/live/live-updates.service';
import { apiErrorMessage } from '../../shared/api-error';
import { CargoMap } from '../../shared/cargo-map';
import { ConfirmService } from '../../shared/confirm-dialog';
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
    StatusBadge,
    ParcelTimeline,
    CargoMap,
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

      @if (d.tracking?.route; as route) {
        <mat-card class="map-card">
          <mat-card-header>
            <mat-card-title>Посылка в пути</mat-card-title>
            <span class="spacer"></span>
            <span class="live-pill" [class.connected]="liveState() === 'connected'" [class.connecting]="liveState() === 'connecting'">
              <mat-icon>{{ liveIcons[liveState()] }}</mat-icon>
              {{ liveLabels[liveState()] }}
            </span>
          </mat-card-header>
          <mat-card-content>
            <app-cargo-map [route]="route.geometry" [position]="d.tracking?.position ?? null" />
          </mat-card-content>
        </mat-card>
      }

      <div class="detail-grid">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Детали</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <dl class="props">
              <dt>Маршрут</dt>
              <dd>
                {{ d.parcel.originWarehouse.city }} ({{ d.parcel.originWarehouse.name }}) ->
                {{ d.parcel.destinationWarehouse.city }} ({{ d.parcel.destinationWarehouse.name }})
              </dd>
              <dt>Получатель</dt>
              <dd>{{ d.parcel.recipientName }}, {{ d.parcel.recipientPhone }}</dd>
              <dt>Вес</dt>
              <dd>{{ d.parcel.weightKg | number: '1.0-2' }} кг</dd>
              @if (d.parcel.lengthCm) {
                <dt>Габариты</dt>
                <dd>{{ d.parcel.lengthCm }} x {{ d.parcel.widthCm }} x {{ d.parcel.heightCm }} см</dd>
              }
              <dt>Цена</dt>
              <dd>{{ d.parcel.price | number: '1.2-2' }} EUR</dd>
              <dt>Создана</dt>
              <dd>{{ d.parcel.createdAt | date: 'dd.MM.yyyy HH:mm' }}</dd>
            </dl>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header>
            <mat-card-title>История статусов</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <app-parcel-timeline [events]="d.events" />
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <p>Загрузка...</p>
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
    .map-card { margin-top: 1rem; }
    mat-card-header { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .live-pill {
      display: inline-flex;
      align-items: center;
      gap: .3rem;
      padding: .2rem .55rem;
      border-radius: 999px;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
      font-size: .85rem;
      white-space: nowrap;
    }
    .live-pill.connected { color: #1b5e20; background: #e8f5e9; }
    .live-pill.connecting { color: #8a5a00; background: #fff8e1; }
    .live-pill mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `,
})
export class ParcelDetailPage {
  readonly id = input.required<string>();

  private readonly parcels = inject(ParcelsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly liveUpdates = inject(LiveUpdatesService);
  private readonly confirm = inject(ConfirmService);
  private eventTrackingNumber: string | null = null;
  private positionTruckId: number | null = null;
  private positionSubscription?: Subscription;

  protected readonly detail = signal<ParcelDetail | null>(null);
  protected readonly cancelling = signal(false);
  protected readonly liveState = this.liveUpdates.connectionState;
  protected readonly liveLabels: Record<LiveConnectionState, string> = {
    connected: 'Live',
    connecting: 'Подключение',
    disconnected: 'Offline',
  };
  protected readonly liveIcons: Record<LiveConnectionState, string> = {
    connected: 'sensors',
    connecting: 'sync',
    disconnected: 'sensors_off',
  };

  constructor() {
    queueMicrotask(() => {
      this.load();
    });
  }

  protected cancel(): void {
    this.confirm
      .confirm({
        title: 'Отменить посылку?',
        message: 'Это действие необратимо.',
        confirmText: 'Отменить посылку',
        warn: true,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.cancelConfirmed();
        }
      });
  }

  private cancelConfirmed(): void {
    this.cancelling.set(true);
    this.parcels.cancel(Number(this.id())).subscribe({
      next: () => {
        this.snackBar.open('Посылка отменена', 'OK', { duration: 5000 });
        this.cancelling.set(false);
        this.load();
      },
      error: (err) => {
        this.cancelling.set(false);
        this.snackBar.open(apiErrorMessage(err, 'Не удалось отменить'), 'OK', { duration: 5000 });
      },
    });
  }

  private load(): void {
    this.parcels
      .detail(Number(this.id()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          this.subscribeToParcelEvents(detail);
          this.subscribeToPosition(detail);
        },
        error: (err) => this.snackBar.open(apiErrorMessage(err, 'Не удалось загрузить посылку'), 'OK', { duration: 5000 }),
      });
  }

  private subscribeToParcelEvents(detail: ParcelDetail): void {
    const trackingNumber = detail.parcel.trackingNumber;
    if (!trackingNumber || trackingNumber === this.eventTrackingNumber) {
      return;
    }
    this.eventTrackingNumber = trackingNumber;
    this.liveUpdates
      .watch<ParcelLiveUpdate>(`/topic/parcels/${trackingNumber}/events`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: () => {
          this.eventTrackingNumber = null;
        },
      });
  }

  private subscribeToPosition(detail: ParcelDetail): void {
    const truckId = detail.tracking?.truckId ?? null;
    if (!truckId) {
      this.positionSubscription?.unsubscribe();
      this.positionSubscription = undefined;
      this.positionTruckId = null;
      return;
    }
    if (truckId === this.positionTruckId) {
      return;
    }
    this.positionSubscription?.unsubscribe();
    this.positionTruckId = truckId;
    this.positionSubscription = this.liveUpdates
      .watch<TruckPosition>(`/topic/trucks/${truckId}/position`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (position) => {
          if (this.positionTruckId !== truckId) {
            return;
          }
          this.detail.update((current) =>
            current?.tracking
              ? {
                  ...current,
                  tracking: { ...current.tracking, position },
                }
              : current,
          );
        },
        error: () => {
          if (this.positionTruckId === truckId) {
            this.positionTruckId = null;
            this.positionSubscription = undefined;
          }
        },
      });
  }
}
