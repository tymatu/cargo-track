import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { DriverService } from '../../core/api/driver.service';
import { SHIPMENT_STATUS_LABELS, Shipment, ShipmentLiveUpdate, TruckPosition } from '../../core/api/models';
import { LiveUpdatesService } from '../../core/live/live-updates.service';
import { apiErrorMessage } from '../../shared/api-error';
import { CargoMap } from '../../shared/cargo-map';
import { ConfirmService } from '../../shared/confirm-dialog';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-driver-shipment-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CargoMap,
    StatusBadge,
  ],
  template: `
    @if (shipment(); as current) {
      <div class="page-header">
        <a matIconButton routerLink="/driver" aria-label="Назад"><mat-icon>arrow_back</mat-icon></a>
        <div>
          <h2>{{ current.originWarehouse.city }} → {{ current.destinationWarehouse.city }}</h2>
          <p>Рейс #{{ current.id }} · {{ labels[current.status] }}</p>
        </div>
      </div>

      <mat-card class="route-card">
        <mat-card-content>
          <div class="route">
            <div class="warehouse">
              <mat-icon>warehouse</mat-icon>
              <div>
                <small>Откуда</small>
                <strong>{{ current.originWarehouse.city }}</strong>
                <span>{{ current.originWarehouse.address }}</span>
              </div>
            </div>
            <mat-icon class="route-arrow">arrow_forward</mat-icon>
            <div class="warehouse destination">
              <mat-icon>flag</mat-icon>
              <div>
                <small>Куда</small>
                <strong>{{ current.destinationWarehouse.city }}</strong>
                <span>{{ current.destinationWarehouse.address }}</span>
              </div>
            </div>
          </div>

          <div class="truck">
            <mat-icon>local_shipping</mat-icon>
            <div>
              <small>Машина</small>
              <strong>{{ current.truck.plateNumber }}</strong>
              <span>{{ current.truck.model }}</span>
            </div>
            <div class="weight">
              <small>Груз</small>
              <strong>{{ current.loadedWeightKg | number: '1.0-2' }} кг</strong>
              <span>{{ current.parcels.length }} мест</span>
            </div>
          </div>

          <div class="times">
            <span>
              <small>План</small>
              {{ (current.plannedDepartureAt || current.createdAt) | date: 'dd.MM HH:mm' }}
            </span>
            @if (current.departedAt) {
              <span><small>Выехал</small>{{ current.departedAt | date: 'dd.MM HH:mm' }}</span>
            }
            @if (current.arrivedAt) {
              <span><small>Прибыл</small>{{ current.arrivedAt | date: 'dd.MM HH:mm' }}</span>
            }
          </div>
        </mat-card-content>
      </mat-card>

      @if (current.route; as route) {
        <mat-card class="map-card">
          <mat-card-header>
            <mat-card-title>Маршрут</mat-card-title>
            <mat-card-subtitle>
              {{ route.distanceKm | number: '1.0-1' }} км · около {{ route.durationMin }} мин
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <app-cargo-map [route]="route.geometry" [position]="current.position" />
          </mat-card-content>
        </mat-card>
      }

      @if (current.status === 'LOADING') {
        <button
          matButton="filled"
          class="primary-action depart"
          [disabled]="submitting()"
          (click)="depart()"
        >
          @if (submitting()) {
            <mat-spinner diameter="28" />
          } @else {
            <mat-icon>departure_board</mat-icon>
          }
          Выехал
        </button>
      }

      @if (current.status === 'IN_TRANSIT') {
        <button
          matButton="filled"
          class="primary-action arrive"
          [disabled]="submitting()"
          (click)="arrive()"
        >
          @if (submitting()) {
            <mat-spinner diameter="28" />
          } @else {
            <mat-icon>where_to_vote</mat-icon>
          }
          Прибыл на склад
        </button>
      }

      <mat-card class="cargo-card">
        <mat-card-header><mat-card-title>Груз</mat-card-title></mat-card-header>
        <mat-card-content>
          <div class="parcel-list">
            @for (parcel of current.parcels; track parcel.id) {
              <div class="parcel">
                <mat-icon>package_2</mat-icon>
                <div>
                  <strong>{{ parcel.trackingNumber }}</strong>
                  <app-status-badge [status]="parcel.status" />
                </div>
                <span>{{ parcel.weightKg | number: '1.0-2' }} кг</span>
              </div>
            } @empty {
              <p class="empty">В рейсе нет посылок</p>
            }
          </div>
        </mat-card-content>
      </mat-card>
    } @else {
      <div class="loading"><mat-spinner diameter="48" /><span>Загружаем рейс…</span></div>
    }
  `,
  styles: `
    :host { display: block; max-width: 760px; margin: 0 auto; }
    .page-header { display: flex; align-items: center; gap: .75rem; margin-bottom: 1rem; }
    h2, p { margin: 0; }
    .page-header p { margin-top: .25rem; opacity: .7; }
    .route { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 1rem; }
    .warehouse, .truck { display: flex; align-items: center; gap: .75rem; }
    .warehouse div, .truck div { display: grid; gap: .1rem; }
    .warehouse small, .truck small, .times small { opacity: .6; }
    .warehouse strong { font-size: 1.15rem; }
    .warehouse span, .truck span { opacity: .75; }
    .destination { text-align: right; justify-content: flex-end; }
    .route-arrow { opacity: .5; }
    .truck {
      margin-top: 1.25rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
    .weight { margin-left: auto; text-align: right; }
    .times { display: flex; gap: 1.5rem; margin-top: 1rem; flex-wrap: wrap; }
    .times span { display: grid; gap: .1rem; }
    .primary-action {
      width: 100%;
      min-height: 72px;
      margin: 1rem 0;
      font-size: 1.25rem;
      font-weight: 600;
    }
    .primary-action mat-icon { transform: scale(1.25); margin-right: .5rem; }
    .arrive { --mat-button-filled-container-color: #2e7d32; }
    .cargo-card, .map-card { margin-top: 1rem; }
    .parcel-list { display: grid; }
    .parcel {
      display: grid;
      grid-template-columns: auto 1fr auto;
      align-items: center;
      gap: .75rem;
      padding: .8rem 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .parcel div { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .parcel strong { font-family: monospace; }
    .empty { opacity: .65; }
    .loading { display: grid; place-items: center; gap: 1rem; min-height: 50vh; }
    @media (max-width: 600px) {
      :host { margin: 0 -.25rem; }
      .route { grid-template-columns: 1fr; }
      .route-arrow { transform: rotate(90deg); justify-self: center; }
      .destination { text-align: left; justify-content: flex-start; }
      .primary-action { position: sticky; bottom: .75rem; z-index: 2; }
      .truck { align-items: flex-start; }
      .weight { margin-left: auto; }
      .parcel div { display: grid; gap: .3rem; }
    }
  `,
})
export class DriverShipmentDetail {
  readonly id = input.required<string>();

  private readonly driver = inject(DriverService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly liveUpdates = inject(LiveUpdatesService);
  private readonly confirm = inject(ConfirmService);

  protected readonly labels = SHIPMENT_STATUS_LABELS;
  protected readonly shipment = signal<Shipment | null>(null);
  protected readonly submitting = signal(false);
  private positionTruckId: number | null = null;

  constructor() {
    queueMicrotask(() => {
      const shipmentId = Number(this.id());
      this.driver
        .shipment(shipmentId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (shipment) => {
            this.shipment.set(shipment);
            this.subscribeToTruckPosition(shipment);
          },
          error: (err) => this.showError(err, 'Не удалось загрузить рейс'),
        });
      this.liveUpdates
        .watch<ShipmentLiveUpdate>(`/topic/shipments/${shipmentId}/position`)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (update) =>
            this.shipment.update((shipment) =>
              shipment
                ? {
                    ...shipment,
                    status: update.status,
                    arrivedAt: update.arrivedAt,
                    position: update.position ?? shipment.position,
                    truck: { ...shipment.truck, status: update.truckStatus },
                  }
                : shipment,
            ),
          error: () => undefined,
        });
    });
  }

  protected depart(): void {
    this.confirm
      .confirm({
        title: 'Подтвердить выезд?',
        message: 'Статусы всех посылок изменятся на «В пути».',
        confirmText: 'Выехать',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.runAction(
            () => this.driver.depart(Number(this.id())),
            'Рейс начат. Хорошей дороги!',
            'Не удалось начать рейс',
          );
        }
      });
  }

  protected arrive(): void {
    this.confirm
      .confirm({
        title: 'Подтвердить прибытие?',
        message: 'Рейс будет завершён на складе назначения.',
        confirmText: 'Подтвердить',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.runAction(
            () => this.driver.arrive(Number(this.id())),
            'Прибытие подтверждено',
            'Не удалось завершить рейс',
          );
        }
      });
  }

  private runAction(
    request: () => ReturnType<DriverService['depart']>,
    successMessage: string,
    errorMessage: string,
  ): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    request().subscribe({
      next: (shipment) => {
        this.shipment.set(shipment);
        this.subscribeToTruckPosition(shipment);
        this.submitting.set(false);
        this.snackBar.open(successMessage, 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.submitting.set(false);
        this.showError(err, errorMessage);
      },
    });
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }

  private subscribeToTruckPosition(shipment: Shipment): void {
    const truckId = shipment.truck.id;
    if (truckId === this.positionTruckId) {
      return;
    }
    this.positionTruckId = truckId;
    this.liveUpdates
      .watch<TruckPosition>(`/topic/trucks/${truckId}/position`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (position) =>
          this.shipment.update((current) => current ? { ...current, position } : current),
        error: () => {
          if (this.positionTruckId === truckId) {
            this.positionTruckId = null;
          }
        },
      });
  }
}
