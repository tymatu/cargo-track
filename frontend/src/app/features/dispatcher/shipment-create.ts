import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { DispatcherService } from '../../core/api/dispatcher.service';
import { CreateShipmentRequest, Truck, Warehouse } from '../../core/api/models';
import { WarehousesService } from '../../core/api/warehouses.service';
import { User } from '../../core/auth/models';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-shipment-create',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <div class="page-header">
      <a matIconButton routerLink="/dispatcher" aria-label="Назад"><mat-icon>arrow_back</mat-icon></a>
      <div>
        <h2>Новый рейс</h2>
        <p>Выберите свободную машину, водителя и склад назначения</p>
      </div>
    </div>

    <mat-card>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Машина</mat-label>
            <mat-select formControlName="truckId" (selectionChange)="truckChanged()">
              @for (truck of trucks(); track truck.id) {
                <mat-option [value]="truck.id">
                  {{ truck.plateNumber }} · {{ truck.model }} ·
                  {{ truck.capacityKg | number: '1.0-2' }} кг
                </mat-option>
              }
            </mat-select>
            @if (!trucks().length) {
              <mat-hint>На складе нет свободных машин</mat-hint>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Водитель</mat-label>
            <mat-select formControlName="driverId">
              @for (driver of availableDrivers(); track driver.id) {
                <mat-option [value]="driver.id">{{ driver.firstName }} {{ driver.lastName }}</mat-option>
              }
            </mat-select>
            @if (!availableDrivers().length) {
              <mat-hint>На складе нет свободных водителей</mat-hint>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Склад назначения</mat-label>
            <mat-select formControlName="destinationWarehouseId">
              @for (warehouse of destinations(); track warehouse.id) {
                <mat-option [value]="warehouse.id">{{ warehouse.city }} · {{ warehouse.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Плановое отправление</mat-label>
            <input matInput type="datetime-local" formControlName="plannedDepartureAt" />
          </mat-form-field>

          <div class="actions">
            <a matButton routerLink="/dispatcher">Отмена</a>
            <button matButton="filled" type="submit" [disabled]="form.invalid || submitting()">
              {{ submitting() ? 'Создаём…' : 'Создать рейс' }}
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .page-header { display: flex; align-items: center; gap: .75rem; margin-bottom: 1rem; }
    h2, p { margin: 0; }
    p { opacity: .7; margin-top: .25rem; }
    mat-card { max-width: 680px; margin: 0 auto; }
    form { display: flex; flex-direction: column; padding-top: .5rem; }
    .actions { display: flex; justify-content: flex-end; gap: .75rem; }
  `,
})
export class ShipmentCreate {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly dispatcher = inject(DispatcherService);
  private readonly warehousesService = inject(WarehousesService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly trucks = signal<Truck[]>([]);
  protected readonly drivers = signal<User[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly submitting = signal(false);

  protected readonly form = this.fb.group({
    truckId: [null as number | null, Validators.required],
    driverId: [null as number | null, Validators.required],
    destinationWarehouseId: [null as number | null, Validators.required],
    plannedDepartureAt: [''],
  });
  private readonly selectedTruckId = toSignal(this.form.controls.truckId.valueChanges, {
    initialValue: this.form.controls.truckId.value,
  });
  private readonly selectedTruck = computed(() =>
    this.trucks().find((truck) => truck.id === this.selectedTruckId()),
  );
  protected readonly availableDrivers = computed(() => {
    const warehouseId = this.selectedTruck()?.homeWarehouseId;
    return warehouseId
      ? this.drivers().filter((driver) => driver.warehouseId === warehouseId)
      : this.drivers();
  });
  protected readonly destinations = computed(() => {
    const warehouseId = this.selectedTruck()?.homeWarehouseId;
    return this.warehouses().filter((warehouse) => warehouse.id !== warehouseId);
  });

  constructor() {
    forkJoin({
      trucks: this.dispatcher.trucks(),
      drivers: this.dispatcher.drivers(),
      warehouses: this.warehousesService.warehouses$,
    }).subscribe({
      next: ({ trucks, drivers, warehouses }) => {
        this.trucks.set(trucks);
        this.drivers.set(drivers);
        this.warehouses.set(warehouses);
      },
      error: (err) => this.showError(err, 'Не удалось загрузить данные для рейса'),
    });
  }

  protected truckChanged(): void {
    this.form.controls.driverId.reset();
    this.form.controls.destinationWarehouseId.reset();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    const value = this.form.getRawValue();
    if (value.truckId === null || value.driverId === null || value.destinationWarehouseId === null) {
      this.showError(null, 'Выберите машину, водителя и склад назначения');
      return;
    }
    this.submitting.set(true);
    const request: CreateShipmentRequest = {
      truckId: value.truckId,
      driverId: value.driverId,
      destinationWarehouseId: value.destinationWarehouseId,
    };
    if (value.plannedDepartureAt) {
      const plannedDepartureAt = this.isoFromLocalDateTime(value.plannedDepartureAt);
      if (!plannedDepartureAt) {
        this.showError(null, 'Укажите корректное плановое отправление');
        return;
      }
      request.plannedDepartureAt = plannedDepartureAt;
    }
    this.dispatcher.createShipment(request).subscribe({
      next: (shipment) => {
        this.snackBar.open('Рейс создан. Можно начинать погрузку.', 'OK', { duration: 5000 });
        this.router.navigate(['/dispatcher/shipments', shipment.id]);
      },
      error: (err) => {
        this.submitting.set(false);
        this.showError(err, 'Не удалось создать рейс');
      },
    });
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }

  private isoFromLocalDateTime(value: string): string | null {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
}
