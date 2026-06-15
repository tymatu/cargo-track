import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { DriverService } from '../../core/api/driver.service';
import {
  Page,
  SHIPMENT_STATUS_LABELS,
  Shipment,
  ShipmentStatus,
} from '../../core/api/models';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-driver-shipment-list',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatSelectModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 data-testid="driver-shipments-title">Мои рейсы</h2>
        <p>Назначенные маршруты и текущие поездки</p>
      </div>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Статус</mat-label>
        <mat-select [ngModel]="status()" (ngModelChange)="changeStatus($event)">
          <mat-option [value]="null">Все рейсы</mat-option>
          @for (entry of statusOptions; track entry[0]) {
            <mat-option [value]="entry[0]">{{ entry[1] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <div class="shipment-grid" data-testid="driver-shipments-grid">
      @for (shipment of page().content; track shipment.id) {
        <a class="shipment-link" [routerLink]="['/driver/shipments', shipment.id]">
          <mat-card>
            <mat-card-content>
              <div class="card-top">
                <span class="route">
                  {{ shipment.originWarehouse.city }}
                  <mat-icon>arrow_forward</mat-icon>
                  {{ shipment.destinationWarehouse.city }}
                </span>
                <span class="status" [class]="'status status-' + shipment.status.toLowerCase()">
                  {{ labels[shipment.status] }}
                </span>
              </div>

              <div class="truck">
                <mat-icon>local_shipping</mat-icon>
                <strong>{{ shipment.truck.plateNumber }}</strong>
                <span>{{ shipment.truck.model }}</span>
              </div>

              <dl>
                <dt>Груз</dt>
                <dd>
                  {{ shipment.parcels.length }} шт. ·
                  {{ shipment.loadedWeightKg | number: '1.0-2' }} кг
                </dd>
                <dt>Отправление</dt>
                <dd>
                  {{
                    (shipment.departedAt || shipment.plannedDepartureAt || shipment.createdAt)
                      | date: 'dd.MM.yyyy HH:mm'
                  }}
                </dd>
              </dl>
            </mat-card-content>
          </mat-card>
        </a>
      } @empty {
        <mat-card class="empty-card">
          <mat-card-content>
            <mat-icon>route</mat-icon>
            <h3>Рейсов пока нет</h3>
            <p>Когда диспетчер назначит рейс, он появится здесь.</p>
          </mat-card-content>
        </mat-card>
      }
    </div>

    <mat-paginator
      [length]="page().totalElements"
      [pageIndex]="page().page"
      [pageSize]="page().size"
      [pageSizeOptions]="[10, 20, 50]"
      (page)="pageChanged($event)"
    />
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
      flex-wrap: wrap;
    }
    h2, h3, p { margin: 0; }
    .page-header p { margin-top: .25rem; opacity: .7; }
    mat-form-field { width: 220px; }
    .shipment-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
    }
    .shipment-link { color: inherit; text-decoration: none; }
    .shipment-link mat-card { height: 100%; transition: transform 120ms ease, box-shadow 120ms ease; }
    .shipment-link:hover mat-card { transform: translateY(-2px); box-shadow: var(--mat-sys-level3); }
    .card-top, .truck { display: flex; align-items: center; gap: .5rem; }
    .card-top { justify-content: space-between; }
    .route { display: flex; align-items: center; font-size: 1.15rem; font-weight: 600; }
    .route mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .truck { margin: 1.25rem 0; }
    .truck span { opacity: .7; }
    .status { padding: .2rem .65rem; border-radius: 1rem; white-space: nowrap; }
    .status-planned, .status-loading { background: #fff3e0; color: #9a5b00; }
    .status-in_transit { background: #ede7f6; color: #5e35b1; }
    .status-completed { background: #e8f5e9; color: #2e7d32; }
    .status-cancelled { background: #fbe9e7; color: #c62828; }
    dl { display: grid; grid-template-columns: auto 1fr; gap: .45rem 1rem; margin: 0; }
    dt { opacity: .65; }
    dd { margin: 0; text-align: right; }
    .empty-card { grid-column: 1 / -1; text-align: center; }
    .empty-card mat-icon { font-size: 52px; width: 52px; height: 52px; opacity: .5; }
    mat-paginator { margin-top: 1rem; }
    @media (max-width: 600px) {
      .page-header { align-items: stretch; }
      mat-form-field { width: 100%; }
      .shipment-grid { grid-template-columns: 1fr; }
    }
  `,
})
export class DriverShipmentList {
  private readonly driver = inject(DriverService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly labels = SHIPMENT_STATUS_LABELS;
  protected readonly statusOptions = Object.entries(SHIPMENT_STATUS_LABELS);
  protected readonly status = signal<ShipmentStatus | null>(null);
  protected readonly page = signal<Page<Shipment>>({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  constructor() {
    this.load(0, 20);
  }

  protected changeStatus(status: ShipmentStatus | null): void {
    this.status.set(status);
    this.load(0, this.page().size);
  }

  protected pageChanged(event: PageEvent): void {
    this.load(event.pageIndex, event.pageSize);
  }

  private load(page: number, size: number): void {
    this.driver.myShipments(page, size, this.status() ?? undefined).subscribe({
      next: (result) => this.page.set(result),
      error: (err) =>
        this.snackBar.open(apiErrorMessage(err, 'Не удалось загрузить рейсы'), 'OK', {
          duration: 5000,
        }),
    });
  }
}
