import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { map, switchMap } from 'rxjs';
import { DispatcherService } from '../../core/api/dispatcher.service';
import {
  Parcel,
  SHIPMENT_STATUS_LABELS,
  Shipment,
  ShipmentLiveUpdate,
  TruckPosition,
} from '../../core/api/models';
import { LiveUpdatesService } from '../../core/live/live-updates.service';
import { apiErrorMessage } from '../../shared/api-error';
import { CargoMap } from '../../shared/cargo-map';

@Component({
  selector: 'app-shipment-load',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressBarModule,
    CargoMap,
  ],
  template: `
    @if (shipment(); as current) {
      <div class="page-header">
        <a matIconButton routerLink="/dispatcher" aria-label="Назад"><mat-icon>arrow_back</mat-icon></a>
        <div>
          <h2>{{ current.originWarehouse.city }} → {{ current.destinationWarehouse.city }}</h2>
          <p>
            Рейс #{{ current.id }} · {{ current.truck.plateNumber }} ·
            {{ current.driver.firstName }} {{ current.driver.lastName }}
          </p>
        </div>
        <span class="spacer"></span>
        <strong>{{ shipmentLabels[current.status] }}</strong>
      </div>

      <mat-card class="capacity-card">
        <mat-card-content>
          <div class="capacity-title">
            <span>Загрузка машины</span>
            <strong>
              {{ projectedWeight() | number: '1.0-2' }} /
              {{ current.truck.capacityKg | number: '1.0-2' }} кг
            </strong>
          </div>
          <mat-progress-bar mode="determinate" [value]="capacityPercent()" />
          @if (selectedWeight() > 0) {
            <p class="selection-note">
              Уже загружено {{ current.loadedWeightKg | number: '1.0-2' }} кг,
              выбрано ещё {{ selectedWeight() | number: '1.0-2' }} кг
            </p>
          }
        </mat-card-content>
      </mat-card>

      @if (current.route; as route) {
        <mat-card class="map-card">
          <mat-card-header>
            <mat-card-title>Маршрут рейса</mat-card-title>
            <mat-card-subtitle>{{ route.distanceKm | number: '1.0-1' }} км</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <app-cargo-map [route]="route.geometry" [position]="current.position" />
          </mat-card-content>
        </mat-card>
      }

      <div class="columns">
        <mat-card>
          <mat-card-header><mat-card-title>Готовы к погрузке</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="parcel-list">
              @for (parcel of availableParcels(); track parcel.id) {
                <label class="parcel-row">
                  <mat-checkbox
                    [checked]="selectedIds().has(parcel.id)"
                    (change)="toggle(parcel, $event.checked)"
                    [disabled]="!canLoad()"
                  />
                  <span class="mono">{{ parcel.trackingNumber }}</span>
                  <span>{{ parcel.recipientName }}</span>
                  <strong>{{ parcel.weightKg | number: '1.0-2' }} кг</strong>
                </label>
              } @empty {
                <p class="empty">Принятых посылок по этому маршруту нет</p>
              }
            </div>
            <button
              matButton="filled"
              class="load-button"
              [disabled]="!selectedIds().size || loading() || !canLoad()"
              (click)="loadSelected()"
            >
              <mat-icon>inventory_2</mat-icon>
              Загрузить выбранные
            </button>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>В машине</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="parcel-list">
              @for (parcel of current.parcels; track parcel.id) {
                <div class="parcel-row">
                  <mat-icon>package_2</mat-icon>
                  <span class="mono">{{ parcel.trackingNumber }}</span>
                  <span>{{ parcel.loadedAt | date: 'dd.MM HH:mm' }}</span>
                  <strong>{{ parcel.weightKg | number: '1.0-2' }} кг</strong>
                  @if (current.status === 'LOADING') {
                    <button
                      matIconButton
                      title="Снять с рейса"
                      aria-label="Снять с рейса"
                      (click)="remove(parcel.id)"
                    >
                      <mat-icon>remove_circle_outline</mat-icon>
                    </button>
                  }
                </div>
              } @empty {
                <p class="empty">Погрузка ещё не началась</p>
              }
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <p>Загрузка рейса…</p>
    }
  `,
  styles: `
    .page-header { display: flex; align-items: center; gap: .75rem; margin-bottom: 1rem; flex-wrap: wrap; }
    h2, p { margin: 0; }
    .page-header p { opacity: .7; margin-top: .25rem; }
    .spacer { flex: 1 1 auto; }
    .capacity-card { margin-bottom: 1rem; }
    .map-card { margin-bottom: 1rem; }
    .capacity-title { display: flex; justify-content: space-between; margin-bottom: .6rem; }
    .selection-note { margin-top: .6rem; opacity: .75; }
    .columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    .parcel-list { display: grid; }
    .parcel-row {
      display: grid;
      grid-template-columns: auto minmax(130px, 1fr) 1fr auto auto;
      align-items: center;
      gap: .75rem;
      min-height: 48px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    label.parcel-row { cursor: pointer; }
    .mono { font-family: monospace; }
    .empty { padding: 1rem 0; opacity: .65; }
    .load-button { margin-top: 1rem; }
    @media (max-width: 850px) {
      .columns { grid-template-columns: 1fr; }
      .parcel-row { grid-template-columns: auto 1fr auto; }
      .parcel-row span:nth-of-type(2) { display: none; }
    }
  `,
})
export class ShipmentLoad {
  readonly id = input.required<string>();

  private readonly dispatcher = inject(DispatcherService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly liveUpdates = inject(LiveUpdatesService);

  protected readonly shipmentLabels = SHIPMENT_STATUS_LABELS;
  protected readonly shipment = signal<Shipment | null>(null);
  protected readonly queue = signal<Parcel[]>([]);
  protected readonly selectedIds = signal<ReadonlySet<number>>(new Set());
  protected readonly loading = signal(false);
  private positionTruckId: number | null = null;

  protected readonly availableParcels = computed(() => {
    return this.queue();
  });
  protected readonly selectedWeight = computed(() =>
    this.availableParcels()
      .filter((parcel) => this.selectedIds().has(parcel.id))
      .reduce((total, parcel) => total + parcel.weightKg, 0),
  );
  protected readonly projectedWeight = computed(
    () => (this.shipment()?.loadedWeightKg ?? 0) + this.selectedWeight(),
  );
  protected readonly capacityPercent = computed(() => {
    const capacity = this.shipment()?.truck.capacityKg ?? 0;
    return capacity ? Math.min(100, (this.projectedWeight() / capacity) * 100) : 0;
  });
  protected readonly canLoad = computed(() => {
    const status = this.shipment()?.status;
    return status === 'PLANNED' || status === 'LOADING';
  });

  constructor() {
    queueMicrotask(() => {
      this.refresh();
      const shipmentId = Number(this.id());
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

  protected toggle(parcel: Parcel, checked: boolean): void {
    const ids = new Set(this.selectedIds());
    checked ? ids.add(parcel.id) : ids.delete(parcel.id);
    this.selectedIds.set(ids);
  }

  protected loadSelected(): void {
    if (!this.selectedIds().size || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.dispatcher.loadParcels(Number(this.id()), [...this.selectedIds()]).subscribe({
      next: (shipment) => {
        this.shipment.set(shipment);
        this.subscribeToTruckPosition(shipment);
        this.selectedIds.set(new Set());
        this.loading.set(false);
        this.loadQueue();
        this.snackBar.open('Посылки загружены в рейс', 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.loading.set(false);
        this.showError(err, 'Не удалось загрузить посылки');
      },
    });
  }

  protected remove(parcelId: number): void {
    this.dispatcher.removeParcel(Number(this.id()), parcelId).subscribe({
      next: (shipment) => {
        this.shipment.set(shipment);
        this.subscribeToTruckPosition(shipment);
        this.loadQueue();
        this.snackBar.open('Посылка снята с рейса', 'OK', { duration: 4000 });
      },
      error: (err) => this.showError(err, 'Не удалось снять посылку с рейса'),
    });
  }

  private refresh(): void {
    this.dispatcher.shipment(Number(this.id())).pipe(
      switchMap((shipment) =>
        this.dispatcher.parcels(0, 500, {
          status: 'ACCEPTED_AT_ORIGIN',
          destinationWarehouseId: shipment.destinationWarehouse.id,
        }).pipe(map((queue) => ({ shipment, queue }))),
      ),
    ).subscribe({
      next: ({ shipment, queue }) => {
        this.shipment.set(shipment);
        this.subscribeToTruckPosition(shipment);
        this.queue.set(queue.content);
      },
      error: (err) => this.showError(err, 'Не удалось загрузить рейс'),
    });
  }

  private loadQueue(): void {
    const shipment = this.shipment();
    if (!shipment) {
      return;
    }
    this.dispatcher.parcels(0, 500, {
      status: 'ACCEPTED_AT_ORIGIN',
      destinationWarehouseId: shipment.destinationWarehouse.id,
    }).subscribe({
      next: (page) => this.queue.set(page.content),
      error: (err) => this.showError(err, 'Не удалось обновить очередь'),
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
