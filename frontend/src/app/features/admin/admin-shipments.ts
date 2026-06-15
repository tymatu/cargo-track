import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminService } from '../../core/api/admin.service';
import {
  Page,
  SHIPMENT_STATUS_LABELS,
  Shipment,
  ShipmentStatus,
  Warehouse,
} from '../../core/api/models';
import { User } from '../../core/auth/models';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-shipments',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    AdminNav,
  ],
  template: `
    <app-admin-nav />
    <div class="page-header">
      <div>
        <h2>Рейсы</h2>
        <p>Поиск рейсов по статусу, складу и водителю</p>
      </div>
      <span class="spacer"></span>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Статус</mat-label>
        <mat-select data-testid="admin-shipments-status-filter" [(ngModel)]="statusFilter" (selectionChange)="load(0, page().size)">
          <mat-option [value]="undefined">Все</mat-option>
          @for (entry of statusOptions; track entry[0]) {
            <mat-option [value]="entry[0]">{{ entry[1] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Склад</mat-label>
        <mat-select [(ngModel)]="warehouseFilter" (selectionChange)="load(0, page().size)">
          <mat-option [value]="undefined">Все</mat-option>
          @for (warehouse of warehouses(); track warehouse.id) {
            <mat-option [value]="warehouse.id">{{ warehouse.city }} · {{ warehouse.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Водитель</mat-label>
        <mat-select [(ngModel)]="driverFilter" (selectionChange)="load(0, page().size)">
          <mat-option [value]="undefined">Все</mat-option>
          @for (driver of drivers(); track driver.id) {
            <mat-option [value]="driver.id">{{ driver.firstName }} {{ driver.lastName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <button matIconButton type="button" aria-label="Сбросить фильтры" (click)="resetFilters()">
        <mat-icon>filter_alt_off</mat-icon>
      </button>
    </div>

    <mat-card>
      <mat-card-content>
        <div class="table-wrap">
          <table mat-table data-testid="admin-shipments-table" [dataSource]="page().content">
            <ng-container matColumnDef="route">
              <th mat-header-cell *matHeaderCellDef>Маршрут</th>
              <td mat-cell *matCellDef="let shipment">
                <a [routerLink]="['/dispatcher/shipments', shipment.id]">
                  <strong>{{ shipment.originWarehouse.city }} -> {{ shipment.destinationWarehouse.city }}</strong>
                  <small>#{{ shipment.id }} · {{ shipment.createdAt | date: 'dd.MM.yyyy HH:mm' }}</small>
                </a>
              </td>
            </ng-container>
            <ng-container matColumnDef="truck">
              <th mat-header-cell *matHeaderCellDef>Машина</th>
              <td mat-cell *matCellDef="let shipment">
                <strong>{{ shipment.truck.plateNumber }}</strong>
                <small>{{ shipment.truck.model }}</small>
              </td>
            </ng-container>
            <ng-container matColumnDef="driver">
              <th mat-header-cell *matHeaderCellDef>Водитель</th>
              <td mat-cell *matCellDef="let shipment">
                {{ shipment.driver.firstName }} {{ shipment.driver.lastName }}
              </td>
            </ng-container>
            <ng-container matColumnDef="load">
              <th mat-header-cell *matHeaderCellDef>Груз</th>
              <td mat-cell *matCellDef="let shipment">
                {{ shipment.parcels.length }} шт. ·
                {{ shipment.loadedWeightKg | number: '1.0-2' }} / {{ shipment.truck.capacityKg | number: '1.0-2' }} кг
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Статус</th>
              <td mat-cell *matCellDef="let shipment">
                <span class="status" [class]="'status status-' + shipment.status.toLowerCase()">
                  {{ shipmentStatusLabel(shipment.status) }}
                </span>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="columns.length">Рейсов по выбранным фильтрам нет</td>
            </tr>
          </table>
        </div>
        <mat-paginator
          [length]="page().totalElements"
          [pageIndex]="page().page"
          [pageSize]="page().size"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="pageChanged($event)"
        />
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .page-header { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; margin-bottom: 1rem; }
    h2, p { margin: 0; }
    p { opacity: .7; margin-top: .2rem; }
    .spacer { flex: 1 1 auto; }
    mat-form-field { width: 190px; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; min-width: 900px; }
    a { color: inherit; text-decoration: none; }
    a:hover strong { text-decoration: underline; }
    td small { display: block; opacity: .65; margin-top: .15rem; }
    .status { padding: .2rem .65rem; border-radius: 1rem; white-space: nowrap; font-weight: 600; }
    .status-planned, .status-loading { background: #fff3e0; color: #9a5b00; }
    .status-in_transit { background: #ede7f6; color: #5e35b1; }
    .status-completed { background: #e8f5e9; color: #2e7d32; }
    .status-cancelled { background: #fbe9e7; color: #c62828; }
    .empty { padding: 1.5rem; text-align: center; opacity: .65; }
    @media (max-width: 760px) {
      .page-header { align-items: stretch; }
      mat-form-field { width: 100%; }
    }
  `,
})
export class AdminShipments {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly labels = SHIPMENT_STATUS_LABELS;
  protected readonly statusOptions = Object.entries(SHIPMENT_STATUS_LABELS) as [ShipmentStatus, string][];
  protected readonly columns = ['route', 'truck', 'driver', 'load', 'status'];
  protected readonly page = signal<Page<Shipment>>({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly drivers = signal<User[]>([]);
  protected statusFilter: ShipmentStatus | undefined;
  protected warehouseFilter: number | undefined;
  protected driverFilter: number | undefined;

  constructor() {
    forkJoin({
      shipments: this.admin.shipments(0, 20),
      warehouses: this.admin.warehouses(),
      drivers: this.admin.users(0, 100, 'DRIVER', 'ACTIVE'),
    }).subscribe({
      next: ({ shipments, warehouses, drivers }) => {
        this.page.set(shipments);
        this.warehouses.set(warehouses);
        this.drivers.set(drivers.content);
      },
      error: (err) => this.showError(err, 'Не удалось загрузить рейсы'),
    });
  }

  protected load(page: number, size: number): void {
    this.admin
      .shipments(page, size, this.statusFilter, this.warehouseFilter, this.driverFilter)
      .subscribe({
        next: (result) => this.page.set(result),
        error: (err) => this.showError(err, 'Не удалось загрузить рейсы'),
      });
  }

  protected pageChanged(event: PageEvent): void {
    this.load(event.pageIndex, event.pageSize);
  }

  protected resetFilters(): void {
    this.statusFilter = undefined;
    this.warehouseFilter = undefined;
    this.driverFilter = undefined;
    this.load(0, this.page().size);
  }

  protected shipmentStatusLabel(status: ShipmentStatus): string {
    return this.labels[status];
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }
}
