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
import { DispatcherService } from '../../core/api/dispatcher.service';
import {
  Page,
  Parcel,
  ParcelStatus,
  SHIPMENT_STATUS_LABELS,
  Shipment,
  STATUS_LABELS,
} from '../../core/api/models';
import { apiErrorMessage } from '../../shared/api-error';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-dispatcher-dashboard',
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
    StatusBadge,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 data-testid="dispatcher-dashboard-title">Диспетчерская</h2>
        <p>Приём посылок и подготовка рейсов вашего склада</p>
      </div>
      <span class="spacer"></span>
      <a matButton="filled" routerLink="/dispatcher/shipments/new">
        <mat-icon>add_road</mat-icon>
        Создать рейс
      </a>
    </div>

    <mat-card>
      <mat-card-header>
        <mat-card-title>Очередь посылок</mat-card-title>
        <span class="spacer"></span>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Статус</mat-label>
          <mat-select [ngModel]="parcelStatus()" (ngModelChange)="changeStatus($event)">
            @for (option of parcelStatuses; track option.value) {
              <mat-option [value]="option.value">{{ option.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </mat-card-header>
      <mat-card-content>
        <div class="table-wrap">
          <table mat-table data-testid="dispatcher-parcels-table" [dataSource]="parcelPage().content">
            <ng-container matColumnDef="number">
              <th mat-header-cell *matHeaderCellDef>Трек-номер</th>
              <td mat-cell *matCellDef="let parcel" class="mono">{{ parcel.trackingNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="destination">
              <th mat-header-cell *matHeaderCellDef>Назначение</th>
              <td mat-cell *matCellDef="let parcel">{{ parcel.destinationWarehouse.city }}</td>
            </ng-container>
            <ng-container matColumnDef="weight">
              <th mat-header-cell *matHeaderCellDef>Вес</th>
              <td mat-cell *matCellDef="let parcel">{{ parcel.weightKg | number: '1.0-2' }} кг</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Статус</th>
              <td mat-cell *matCellDef="let parcel"><app-status-badge [status]="parcel.status" /></td>
            </ng-container>
            <ng-container matColumnDef="action">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let parcel" class="actions">
                @if (parcel.status === 'CREATED') {
                  <button matButton="filled" (click)="accept(parcel)">Принять</button>
                }
                @if (parcel.status === 'ARRIVED_AT_DESTINATION') {
                  <button matButton="filled" (click)="deliver(parcel)">Выдать</button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="parcelColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: parcelColumns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="parcelColumns.length">Очередь пуста</td>
            </tr>
          </table>
        </div>
        <mat-paginator
          [length]="parcelPage().totalElements"
          [pageIndex]="parcelPage().page"
          [pageSize]="parcelPage().size"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="parcelPageChanged($event)"
        />
      </mat-card-content>
    </mat-card>

    <mat-card class="shipments-card">
      <mat-card-header><mat-card-title>Рейсы</mat-card-title></mat-card-header>
      <mat-card-content>
        <div class="shipment-list" data-testid="dispatcher-shipments-list">
          @for (shipment of shipmentPage().content; track shipment.id) {
            <a class="shipment-row" [routerLink]="['/dispatcher/shipments', shipment.id]">
              <span class="route">{{ shipment.originWarehouse.city }} → {{ shipment.destinationWarehouse.city }}</span>
              <span>{{ shipment.truck.plateNumber }}</span>
              <span>{{ shipment.driver.firstName }} {{ shipment.driver.lastName }}</span>
              <span>{{ shipment.loadedWeightKg | number: '1.0-2' }} / {{ shipment.truck.capacityKg | number: '1.0-2' }} кг</span>
              <strong>{{ shipmentLabels[shipment.status] }}</strong>
              <span>{{ shipment.createdAt | date: 'dd.MM.yyyy HH:mm' }}</span>
            </a>
          } @empty {
            <p class="empty">Рейсов пока нет</p>
          }
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .page-header, mat-card-header { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .page-header { margin-bottom: 1rem; }
    h2, p { margin: 0; }
    .page-header p { opacity: .7; margin-top: .25rem; }
    .spacer { flex: 1 1 auto; }
    mat-form-field { width: 240px; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; }
    .mono { font-family: monospace; }
    .actions { text-align: right; }
    .empty { padding: 1.5rem; text-align: center; opacity: .65; }
    .shipments-card { margin-top: 1rem; }
    .shipment-list { display: grid; }
    .shipment-row {
      display: grid;
      grid-template-columns: 1.4fr repeat(5, minmax(100px, 1fr));
      gap: .75rem;
      align-items: center;
      padding: .85rem 0;
      color: inherit;
      text-decoration: none;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .shipment-row:hover { background: var(--mat-sys-surface-container-low); }
    .route { font-weight: 600; }
    @media (max-width: 900px) { .shipment-row { grid-template-columns: 1fr 1fr; } }
  `,
})
export class DispatcherDashboard {
  private readonly dispatcher = inject(DispatcherService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly parcelColumns = ['number', 'destination', 'weight', 'status', 'action'];
  protected readonly parcelStatuses: { value: ParcelStatus; label: string }[] = [
    { value: 'CREATED', label: STATUS_LABELS.CREATED },
    { value: 'ACCEPTED_AT_ORIGIN', label: STATUS_LABELS.ACCEPTED_AT_ORIGIN },
    { value: 'ARRIVED_AT_DESTINATION', label: STATUS_LABELS.ARRIVED_AT_DESTINATION },
  ];
  protected readonly shipmentLabels = SHIPMENT_STATUS_LABELS;
  protected readonly parcelStatus = signal<ParcelStatus>('CREATED');
  protected readonly parcelPage = signal<Page<Parcel>>(this.emptyPage());
  protected readonly shipmentPage = signal<Page<Shipment>>(this.emptyPage());

  constructor() {
    this.loadParcels(0, 20);
    this.loadShipments();
  }

  protected changeStatus(status: ParcelStatus): void {
    this.parcelStatus.set(status);
    this.loadParcels(0, this.parcelPage().size);
  }

  protected parcelPageChanged(event: PageEvent): void {
    this.loadParcels(event.pageIndex, event.pageSize);
  }

  protected accept(parcel: Parcel): void {
    this.dispatcher.acceptParcel(parcel.id).subscribe({
      next: () => {
        this.snackBar.open(`Посылка ${parcel.trackingNumber} принята`, 'OK', { duration: 4000 });
        this.loadParcels(this.parcelPage().page, this.parcelPage().size);
      },
      error: (err) => this.showError(err, 'Не удалось принять посылку'),
    });
  }

  protected deliver(parcel: Parcel): void {
    this.dispatcher.deliverParcel(parcel.id).subscribe({
      next: () => {
        this.snackBar.open(`Посылка ${parcel.trackingNumber} выдана`, 'OK', { duration: 4000 });
        this.loadParcels(this.parcelPage().page, this.parcelPage().size);
      },
      error: (err) => this.showError(err, 'Не удалось выдать посылку'),
    });
  }

  private loadParcels(page: number, size: number): void {
    this.dispatcher.parcels(page, size, { status: this.parcelStatus() }).subscribe({
      next: (result) => this.parcelPage.set(result),
      error: (err) => this.showError(err, 'Не удалось загрузить очередь'),
    });
  }

  private loadShipments(): void {
    this.dispatcher.shipments(0, 50).subscribe({
      next: (result) => this.shipmentPage.set(result),
      error: (err) => this.showError(err, 'Не удалось загрузить рейсы'),
    });
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }

  private emptyPage<T>(): Page<T> {
    return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  }
}
