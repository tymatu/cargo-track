import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Page, Parcel, ParcelStatus, STATUS_LABELS } from '../../core/api/models';
import { ParcelsService } from '../../core/api/parcels.service';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-parcel-list',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    StatusBadge,
  ],
  template: `
    <div class="list-header">
      <h2>Мои посылки</h2>
      <span class="spacer"></span>
      <mat-form-field appearance="outline" class="status-filter" subscriptSizing="dynamic">
        <mat-label>Статус</mat-label>
        <mat-select [ngModel]="status()" (ngModelChange)="onStatusChange($event)">
          <mat-option [value]="null">Все</mat-option>
          @for (entry of statusOptions; track entry[0]) {
            <mat-option [value]="entry[0]">{{ entry[1] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <a matButton="filled" routerLink="/parcels/new">
        <mat-icon>add</mat-icon>
        Создать посылку
      </a>
    </div>

    <mat-card>
      <table mat-table [dataSource]="page().content">
        <ng-container matColumnDef="trackingNumber">
          <th mat-header-cell *matHeaderCellDef>Трек-номер</th>
          <td mat-cell *matCellDef="let p">
            <a [routerLink]="['/parcels', p.id]" class="tracking-link">{{ p.trackingNumber }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="route">
          <th mat-header-cell *matHeaderCellDef>Маршрут</th>
          <td mat-cell *matCellDef="let p">
            {{ p.originWarehouse.city }} → {{ p.destinationWarehouse.city }}
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Статус</th>
          <td mat-cell *matCellDef="let p"><app-status-badge [status]="p.status" /></td>
        </ng-container>
        <ng-container matColumnDef="price">
          <th mat-header-cell *matHeaderCellDef>Цена</th>
          <td mat-cell *matCellDef="let p">{{ p.price | number: '1.2-2' }} €</td>
        </ng-container>
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>Создана</th>
          <td mat-cell *matCellDef="let p">{{ p.createdAt | date: 'dd.MM.yyyy HH:mm' }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell empty-row" [attr.colspan]="columns.length">
            Посылок пока нет — создайте первую!
          </td>
        </tr>
      </table>

      <mat-paginator
        [length]="page().totalElements"
        [pageIndex]="page().page"
        [pageSize]="page().size"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPage($event)"
      />
    </mat-card>
  `,
  styles: `
    .list-header {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .spacer { flex: 1 1 auto; }
    .status-filter { width: 220px; }
    .tracking-link { font-family: monospace; }
    .empty-row { padding: 1.5rem; text-align: center; opacity: 0.7; }
    table { width: 100%; }
  `,
})
export class ParcelList {
  private readonly parcels = inject(ParcelsService);

  protected readonly statusOptions = Object.entries(STATUS_LABELS);
  protected readonly columns = ['trackingNumber', 'route', 'status', 'price', 'createdAt'];

  protected readonly status = signal<ParcelStatus | null>(null);
  protected readonly page = signal<Page<Parcel>>({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  constructor() {
    this.load(0, 20);
  }

  protected onStatusChange(status: ParcelStatus | null): void {
    this.status.set(status);
    this.load(0, this.page().size);
  }

  protected onPage(event: PageEvent): void {
    this.load(event.pageIndex, event.pageSize);
  }

  private load(page: number, size: number): void {
    this.parcels.my(page, size, this.status()).subscribe((result) => this.page.set(result));
  }
}
