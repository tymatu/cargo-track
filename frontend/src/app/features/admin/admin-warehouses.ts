import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { AdminService } from '../../core/api/admin.service';
import { Warehouse, WarehouseRequest } from '../../core/api/models';
import { apiErrorMessage } from '../../shared/api-error';
import { ConfirmService } from '../../shared/confirm-dialog';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-warehouses',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatTableModule, AdminNav],
  template: `
    <app-admin-nav />
    <h2>Склады</h2>
    <mat-card class="editor">
      <mat-card-header><mat-card-title>{{ editingId() ? 'Редактирование склада' : 'Новый склад' }}</mat-card-title></mat-card-header>
      <mat-card-content><form class="form-grid" (ngSubmit)="save()">
        <mat-form-field appearance="outline"><mat-label>Название</mat-label><input matInput name="name" [(ngModel)]="form.name" required /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Город</mat-label><input matInput name="city" [(ngModel)]="form.city" required /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Адрес</mat-label><input matInput name="address" [(ngModel)]="form.address" required /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Широта</mat-label><input matInput type="number" step="0.000001" name="latitude" [(ngModel)]="form.latitude" required /></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Долгота</mat-label><input matInput type="number" step="0.000001" name="longitude" [(ngModel)]="form.longitude" required /></mat-form-field>
        <div class="buttons"><button matButton="filled" type="submit"><mat-icon>save</mat-icon> Сохранить</button>@if (editingId()) { <button matButton type="button" (click)="reset()">Отмена</button> }</div>
      </form></mat-card-content>
    </mat-card>
    <mat-card><mat-card-content><div class="table-wrap"><table mat-table [dataSource]="warehouses()">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Название</th><td mat-cell *matCellDef="let item"><strong>{{ item.name }}</strong></td></ng-container>
      <ng-container matColumnDef="city"><th mat-header-cell *matHeaderCellDef>Город</th><td mat-cell *matCellDef="let item">{{ item.city }}</td></ng-container>
      <ng-container matColumnDef="address"><th mat-header-cell *matHeaderCellDef>Адрес</th><td mat-cell *matCellDef="let item">{{ item.address }}</td></ng-container>
      <ng-container matColumnDef="coordinates"><th mat-header-cell *matHeaderCellDef>Координаты</th><td mat-cell *matCellDef="let item">{{ item.latitude | number: '1.3-6' }}, {{ item.longitude | number: '1.3-6' }}</td></ng-container>
      <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let item"><button matIconButton title="Редактировать" (click)="edit(item)"><mat-icon>edit</mat-icon></button><button matIconButton title="Удалить" (click)="remove(item)"><mat-icon>delete</mat-icon></button></td></ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr><tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table></div></mat-card-content></mat-card>
  `,
  styles: `
    h2 { margin: 0 0 1rem; } .editor { margin-bottom: 1rem; }
    .form-grid { display: grid; grid-template-columns: repeat(3, minmax(170px, 1fr)); gap: .75rem; align-items: center; }
    .buttons { display: flex; gap: .5rem; } .table-wrap { overflow-x: auto; } table { width: 100%; min-width: 720px; }
    @media (max-width: 800px) { .form-grid { grid-template-columns: 1fr; } }
  `,
})
export class AdminWarehouses {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(ConfirmService);
  protected readonly columns = ['name', 'city', 'address', 'coordinates', 'action'];
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected form: WarehouseRequest = this.emptyForm();

  constructor() { this.reload(); }

  protected save(): void {
    const editingId = this.editingId();
    const request = editingId !== null
      ? this.admin.updateWarehouse(editingId, this.form)
      : this.admin.createWarehouse(this.form);
    request.subscribe({
      next: () => { this.reset(); this.reload(); },
      error: (err) => this.showError(err, 'Не удалось сохранить склад'),
    });
  }

  protected edit(item: Warehouse): void {
    this.editingId.set(item.id);
    this.form = { name: item.name, city: item.city, address: item.address, latitude: item.latitude, longitude: item.longitude };
  }

  protected remove(item: Warehouse): void {
    this.confirm
      .confirm({
        title: 'Удалить склад?',
        message: `${item.city} · ${item.name}`,
        confirmText: 'Удалить',
        warn: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.admin.deleteWarehouse(item.id).subscribe({
          next: () => this.reload(),
          error: (err) => this.showError(err, 'Не удалось удалить склад'),
        });
      });
  }

  protected reset(): void {
    this.editingId.set(null);
    this.form = this.emptyForm();
  }

  private reload(): void {
    this.admin.warehouses().subscribe({
      next: (items) => this.warehouses.set(items),
      error: (err) => this.showError(err, 'Не удалось загрузить склады'),
    });
  }

  private emptyForm(): WarehouseRequest {
    return { name: '', city: '', address: '', latitude: 50.075538, longitude: 14.4378 };
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }
}
