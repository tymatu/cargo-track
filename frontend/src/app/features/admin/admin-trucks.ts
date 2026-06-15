import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import { AdminService } from '../../core/api/admin.service';
import { Truck, TruckRequest, TruckStatus, Warehouse } from '../../core/api/models';
import { apiErrorMessage } from '../../shared/api-error';
import { ConfirmService } from '../../shared/confirm-dialog';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-trucks',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatTableModule, AdminNav],
  template: `
    <app-admin-nav />
    <h2>Машины</h2>
    <mat-card class="editor">
      <mat-card-header><mat-card-title>{{ editingId() ? 'Редактирование машины' : 'Новая машина' }}</mat-card-title></mat-card-header>
      <mat-card-content>
        <form class="form-grid" (ngSubmit)="save()">
          <mat-form-field appearance="outline"><mat-label>Госномер</mat-label><input matInput name="plate" [(ngModel)]="form.plateNumber" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Модель</mat-label><input matInput name="model" [(ngModel)]="form.model" /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Грузоподъемность, кг</mat-label><input matInput type="number" min="0.01" step="0.01" name="capacity" [(ngModel)]="form.capacityKg" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Статус</mat-label><mat-select name="status" [(ngModel)]="form.status"><mat-option value="IDLE">IDLE</mat-option><mat-option value="MAINTENANCE">MAINTENANCE</mat-option></mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Базовый склад</mat-label><mat-select name="warehouse" [(ngModel)]="form.homeWarehouseId">@for (warehouse of warehouses(); track warehouse.id) { <mat-option [value]="warehouse.id">{{ warehouse.city }} · {{ warehouse.name }}</mat-option> }</mat-select></mat-form-field>
          <div class="buttons"><button matButton="filled" type="submit"><mat-icon>save</mat-icon> Сохранить</button>@if (editingId()) { <button matButton type="button" (click)="reset()">Отмена</button> }</div>
        </form>
      </mat-card-content>
    </mat-card>
    <mat-card>
      <mat-card-content><div class="table-wrap"><table mat-table [dataSource]="trucks()">
        <ng-container matColumnDef="plate"><th mat-header-cell *matHeaderCellDef>Госномер</th><td mat-cell *matCellDef="let truck"><strong>{{ truck.plateNumber }}</strong></td></ng-container>
        <ng-container matColumnDef="model"><th mat-header-cell *matHeaderCellDef>Модель</th><td mat-cell *matCellDef="let truck">{{ truck.model || '—' }}</td></ng-container>
        <ng-container matColumnDef="capacity"><th mat-header-cell *matHeaderCellDef>Грузоподъемность</th><td mat-cell *matCellDef="let truck">{{ truck.capacityKg | number: '1.0-2' }} кг</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Статус</th><td mat-cell *matCellDef="let truck">{{ truck.status }}</td></ng-container>
        <ng-container matColumnDef="warehouse"><th mat-header-cell *matHeaderCellDef>Склад</th><td mat-cell *matCellDef="let truck">{{ warehouseName(truck.homeWarehouseId) }}</td></ng-container>
        <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let truck"><button matIconButton title="Редактировать" (click)="edit(truck)"><mat-icon>edit</mat-icon></button><button matIconButton title="Удалить" (click)="remove(truck)"><mat-icon>delete</mat-icon></button></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr><tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table></div></mat-card-content>
    </mat-card>
  `,
  styles: `
    h2 { margin: 0 0 1rem; } .editor { margin-bottom: 1rem; }
    .form-grid { display: grid; grid-template-columns: repeat(3, minmax(170px, 1fr)); gap: .75rem; align-items: center; }
    .buttons { display: flex; gap: .5rem; } .table-wrap { overflow-x: auto; } table { width: 100%; min-width: 760px; }
    @media (max-width: 800px) { .form-grid { grid-template-columns: 1fr; } }
  `,
})
export class AdminTrucks {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(ConfirmService);
  protected readonly columns = ['plate', 'model', 'capacity', 'status', 'warehouse', 'action'];
  protected readonly trucks = signal<Truck[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected form: TruckRequest = this.emptyForm();

  constructor() {
    forkJoin({ trucks: this.admin.trucks(), warehouses: this.admin.warehouses() }).subscribe({
      next: ({ trucks, warehouses }) => {
        this.trucks.set(trucks);
        this.warehouses.set(warehouses);
        this.form.homeWarehouseId = warehouses[0]?.id ?? 0;
      },
      error: (err) => this.showError(err, 'Не удалось загрузить машины'),
    });
  }

  protected save(): void {
    const editingId = this.editingId();
    const request = editingId !== null
      ? this.admin.updateTruck(editingId, this.form)
      : this.admin.createTruck(this.form);
    request.subscribe({
      next: () => { this.reset(); this.reload(); },
      error: (err) => this.showError(err, 'Не удалось сохранить машину'),
    });
  }

  protected edit(truck: Truck): void {
    if (truck.status === 'IN_TRANSIT') {
      this.snackBar.open('Машину в пути нельзя редактировать', 'OK', { duration: 4000 });
      return;
    }
    this.editingId.set(truck.id);
    this.form = { plateNumber: truck.plateNumber, model: truck.model, capacityKg: truck.capacityKg, status: truck.status, homeWarehouseId: truck.homeWarehouseId };
  }

  protected remove(truck: Truck): void {
    this.confirm
      .confirm({
        title: 'Удалить машину?',
        message: truck.plateNumber,
        confirmText: 'Удалить',
        warn: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.admin.deleteTruck(truck.id).subscribe({
          next: () => this.reload(),
          error: (err) => this.showError(err, 'Не удалось удалить машину'),
        });
      });
  }

  protected reset(): void {
    this.editingId.set(null);
    this.form = { ...this.emptyForm(), homeWarehouseId: this.warehouses()[0]?.id ?? 0 };
  }

  protected warehouseName(id: number): string {
    const warehouse = this.warehouses().find((item) => item.id === id);
    return warehouse ? `${warehouse.city} · ${warehouse.name}` : `#${id}`;
  }

  private reload(): void {
    this.admin.trucks().subscribe({
      next: (trucks) => this.trucks.set(trucks),
      error: (err) => this.showError(err, 'Не удалось загрузить машины'),
    });
  }

  private emptyForm(): TruckRequest {
    return { plateNumber: '', model: '', capacityKg: 1000, status: 'IDLE' as TruckStatus, homeWarehouseId: 0 };
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }
}
