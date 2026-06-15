import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { AdminService, AuditFilters } from '../../core/api/admin.service';
import { AuditLog, Page } from '../../core/api/models';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-audit',
  imports: [
    DatePipe,
    JsonPipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    AdminNav,
  ],
  template: `
    <app-admin-nav />

    <div class="page-header">
      <div>
        <h2 data-testid="admin-audit-title">Аудит-лог</h2>
        <p>Кто, что, где и когда изменил в системе</p>
      </div>
      <span class="spacer"></span>
      <button matButton type="button" (click)="clear()">
        <mat-icon>restart_alt</mat-icon>
        Сбросить
      </button>
      <button matButton="filled" type="button" (click)="search()">
        <mat-icon>search</mat-icon>
        Найти
      </button>
    </div>

    <mat-card class="filters">
      <mat-card-content>
        <div class="filter-grid">
          <mat-form-field appearance="outline">
            <mat-label>ID пользователя</mat-label>
            <input matInput type="number" [(ngModel)]="filters.userId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Действие</mat-label>
            <mat-select [(ngModel)]="filters.action">
              <mat-option [value]="undefined">Все</mat-option>
              @for (action of actions; track action) {
                <mat-option [value]="action">{{ action }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Тип сущности</mat-label>
            <input matInput [(ngModel)]="filters.entityType" placeholder="Parcel, Shipment..." />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>ID сущности</mat-label>
            <input matInput type="number" [(ngModel)]="filters.entityId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>С даты</mat-label>
            <input matInput type="datetime-local" [(ngModel)]="fromLocal" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>До даты</mat-label>
            <input matInput type="datetime-local" [(ngModel)]="toLocal" />
          </mat-form-field>
        </div>
      </mat-card-content>
    </mat-card>

    <section class="audit-layout">
      <mat-card>
        <mat-card-content>
          <div class="table-wrap">
            <table mat-table [dataSource]="page().content">
              <ng-container matColumnDef="time">
                <th mat-header-cell *matHeaderCellDef>Когда</th>
                <td mat-cell *matCellDef="let item">{{ item.createdAt | date: 'dd.MM.yyyy HH:mm:ss' }}</td>
              </ng-container>
              <ng-container matColumnDef="actor">
                <th mat-header-cell *matHeaderCellDef>Кто</th>
                <td mat-cell *matCellDef="let item">
                  <strong>{{ item.username || 'SYSTEM' }}</strong>
                  <small>@if (item.userId) { #{{ item.userId }} } {{ item.ipAddress }}</small>
                </td>
              </ng-container>
              <ng-container matColumnDef="action">
                <th mat-header-cell *matHeaderCellDef>Действие</th>
                <td mat-cell *matCellDef="let item">
                  <strong>{{ item.action }}</strong>
                  <small>{{ item.httpMethod }} {{ item.endpoint }}</small>
                </td>
              </ng-container>
              <ng-container matColumnDef="entity">
                <th mat-header-cell *matHeaderCellDef>Объект</th>
                <td mat-cell *matCellDef="let item">
                  {{ item.entityType || '-' }} @if (item.entityId) { #{{ item.entityId }} }
                </td>
              </ng-container>
              <ng-container matColumnDef="details">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let item">
                  <button matButton type="button" (click)="selected.set(item)">Подробнее</button>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr
                mat-row
                *matRowDef="let row; columns: columns"
                [class.selected]="selected()?.id === row.id"
                (click)="selected.set(row)"
              ></tr>
            </table>
          </div>
          <mat-paginator
            [length]="page().totalElements"
            [pageIndex]="page().page"
            [pageSize]="page().size"
            [pageSizeOptions]="[20, 50, 100]"
            (page)="pageChanged($event)"
          />
        </mat-card-content>
      </mat-card>

      <mat-card class="detail-card">
        <mat-card-header>
          <mat-card-title>Детали события</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (selected(); as item) {
            <dl class="props">
              <dt>ID</dt><dd>#{{ item.id }}</dd>
              <dt>Action</dt><dd>{{ item.action }}</dd>
              <dt>Actor</dt><dd>{{ item.username || 'SYSTEM' }}</dd>
              <dt>Endpoint</dt><dd>{{ item.httpMethod }} {{ item.endpoint }}</dd>
              <dt>User-Agent</dt><dd>{{ item.userAgent || '-' }}</dd>
            </dl>
            <div class="json-grid">
              <section>
                <h3>До</h3>
                <pre>{{ item.oldValue | json }}</pre>
              </section>
              <section>
                <h3>После</h3>
                <pre>{{ item.newValue | json }}</pre>
              </section>
            </div>
          } @else {
            <p class="empty">Выберите строку аудита, чтобы увидеть JSON до и после изменения.</p>
          }
        </mat-card-content>
      </mat-card>
    </section>
  `,
  styles: `
    .page-header { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; margin-bottom: 1rem; }
    h2, h3, p { margin: 0; }
    p { opacity: .7; margin-top: .2rem; }
    .spacer { flex: 1 1 auto; }
    .filters { margin-bottom: 1rem; }
    .filter-grid { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: .75rem; }
    .audit-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(360px, .8fr); gap: 1rem; align-items: start; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; min-width: 920px; }
    tr.selected { background: var(--mat-sys-secondary-container); }
    td small { display: block; opacity: .65; max-width: 280px; overflow: hidden; text-overflow: ellipsis; }
    .detail-card { position: sticky; top: 1rem; }
    .props { display: grid; grid-template-columns: auto 1fr; gap: .45rem .85rem; margin: 0 0 1rem; }
    .props dt { opacity: .65; }
    .props dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .json-grid { display: grid; grid-template-columns: 1fr; gap: .75rem; }
    .json-grid h3 { font-size: .95rem; margin-bottom: .35rem; }
    pre {
      max-height: 240px;
      overflow: auto;
      white-space: pre-wrap;
      font-size: .75rem;
      padding: .75rem;
      border-radius: 8px;
      background: var(--mat-sys-surface-container);
    }
    .empty { padding: 1rem 0; }
    @media (max-width: 1050px) { .audit-layout { grid-template-columns: 1fr; } .detail-card { position: static; } }
    @media (max-width: 800px) { .filter-grid { grid-template-columns: 1fr; } }
  `,
})
export class AdminAudit {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly columns = ['time', 'actor', 'action', 'entity', 'details'];
  protected readonly page = signal<Page<AuditLog>>({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  protected readonly selected = signal<AuditLog | null>(null);
  protected filters: AuditFilters = {};
  protected fromLocal = '';
  protected toLocal = '';
  protected readonly actions = [
    'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGOUT', 'TOKEN_REFRESHED', 'SUSPICIOUS_REFRESH_REUSE',
    'USER_REGISTERED', 'EMPLOYEE_CREATED', 'USER_BLOCKED', 'USER_UNBLOCKED', 'USER_ROLE_CHANGED',
    'PARCEL_CREATED', 'PARCEL_CANCELLED', 'PARCEL_STATUS_CHANGED',
    'SHIPMENT_CREATED', 'SHIPMENT_PARCEL_ASSIGNED', 'SHIPMENT_PARCEL_REMOVED', 'SHIPMENT_DEPARTED',
    'SHIPMENT_ARRIVED', 'TRUCK_CREATED', 'TRUCK_UPDATED', 'TRUCK_DELETED',
    'WAREHOUSE_CREATED', 'WAREHOUSE_UPDATED', 'WAREHOUSE_DELETED',
  ];

  constructor() {
    this.load(0, 20);
  }

  protected search(): void {
    this.load(0, this.page().size);
  }

  protected pageChanged(event: PageEvent): void {
    this.load(event.pageIndex, event.pageSize);
  }

  protected clear(): void {
    this.filters = {};
    this.fromLocal = '';
    this.toLocal = '';
    this.selected.set(null);
    this.load(0, this.page().size);
  }

  private load(page: number, size: number): void {
    const from = this.isoFromLocal(this.fromLocal);
    const to = this.isoFromLocal(this.toLocal);
    if ((this.fromLocal && !from) || (this.toLocal && !to)) {
      this.snackBar.open('Укажите корректный период аудита', 'OK', { duration: 5000 });
      return;
    }
    const filters = {
      ...this.filters,
      from: from ?? undefined,
      to: to ?? undefined,
    };
    this.admin.audit(page, size, filters).subscribe({
      next: (result) => {
        this.page.set(result);
        if (!result.content.some((item) => item.id === this.selected()?.id)) {
          this.selected.set(result.content[0] ?? null);
        }
      },
      error: (err) => this.snackBar.open(
        apiErrorMessage(err, 'Не удалось загрузить аудит'),
        'OK',
        { duration: 5000 },
      ),
    });
  }

  private isoFromLocal(value: string): string | null {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
}
