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
import { forkJoin } from 'rxjs';
import { AdminService } from '../../core/api/admin.service';
import { Page, Warehouse } from '../../core/api/models';
import { Role, User } from '../../core/auth/models';
import { apiErrorMessage } from '../../shared/api-error';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-users',
  imports: [
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
      <div><h2>Пользователи</h2><p>Роли, привязка сотрудников и блокировка доступа</p></div>
      <span class="spacer"></span>
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="search-field">
        <mat-label>Поиск</mat-label>
        <input
          matInput
          name="userSearch"
          [(ngModel)]="searchFilter"
          (keyup.enter)="load(0, page().size)"
        />
      </mat-form-field>
      <button matIconButton type="button" aria-label="Найти" (click)="load(0, page().size)">
        <mat-icon>search</mat-icon>
      </button>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Роль</mat-label>
        <mat-select [(ngModel)]="roleFilter" (selectionChange)="load(0, page().size)">
          <mat-option [value]="undefined">Все</mat-option>
          @for (role of roles; track role) { <mat-option [value]="role">{{ role }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Статус</mat-label>
        <mat-select [(ngModel)]="statusFilter" (selectionChange)="load(0, page().size)">
          <mat-option [value]="undefined">Все</mat-option>
          <mat-option value="ACTIVE">ACTIVE</mat-option>
          <mat-option value="BLOCKED">BLOCKED</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    <mat-card class="create-card">
      <mat-card-header><mat-card-title>Новый сотрудник</mat-card-title></mat-card-header>
      <mat-card-content>
        <form class="form-grid" (ngSubmit)="createEmployee()">
          <mat-form-field appearance="outline"><mat-label>Email</mat-label><input matInput type="email" name="email" [(ngModel)]="employee.email" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Пароль</mat-label><input matInput type="password" name="password" [(ngModel)]="employee.password" minlength="8" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Имя</mat-label><input matInput name="firstName" [(ngModel)]="employee.firstName" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Фамилия</mat-label><input matInput name="lastName" [(ngModel)]="employee.lastName" required /></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Роль</mat-label><mat-select name="role" [(ngModel)]="employee.role"><mat-option value="DRIVER">DRIVER</mat-option><mat-option value="DISPATCHER">DISPATCHER</mat-option></mat-select></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Склад</mat-label><mat-select name="warehouseId" [(ngModel)]="employee.warehouseId" required>@for (warehouse of warehouses(); track warehouse.id) { <mat-option [value]="warehouse.id">{{ warehouse.city }} · {{ warehouse.name }}</mat-option> }</mat-select></mat-form-field>
          <button matButton="filled" type="submit"><mat-icon>person_add</mat-icon> Создать</button>
        </form>
      </mat-card-content>
    </mat-card>

    <mat-card class="table-card">
      <mat-card-content>
        <div class="table-wrap">
          <table mat-table [dataSource]="page().content">
            <ng-container matColumnDef="user"><th mat-header-cell *matHeaderCellDef>Пользователь</th><td mat-cell *matCellDef="let user"><strong>{{ user.firstName }} {{ user.lastName }}</strong><small>{{ user.email }}</small></td></ng-container>
            <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Роль</th><td mat-cell *matCellDef="let user"><mat-select [ngModel]="user.role" (ngModelChange)="changeRole(user, $event)">@for (role of roles; track role) { <mat-option [value]="role">{{ role }}</mat-option> }</mat-select></td></ng-container>
            <ng-container matColumnDef="warehouse"><th mat-header-cell *matHeaderCellDef>Склад</th><td mat-cell *matCellDef="let user">@if (isEmployee(user.role)) { <mat-select [ngModel]="user.warehouseId" (ngModelChange)="changeWarehouse(user, $event)">@for (warehouse of warehouses(); track warehouse.id) { <mat-option [value]="warehouse.id">{{ warehouse.city }}</mat-option> }</mat-select> } @else { <span>—</span> }</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Статус</th><td mat-cell *matCellDef="let user"><span class="status" [class.blocked]="user.status === 'BLOCKED'">{{ user.status }}</span></td></ng-container>
            <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let user">@if (user.status === 'ACTIVE') { <button matButton (click)="setBlocked(user, true)">Блокировать</button> } @else { <button matButton="filled" (click)="setBlocked(user, false)">Разблокировать</button> }</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
        <mat-paginator [length]="page().totalElements" [pageIndex]="page().page" [pageSize]="page().size" [pageSizeOptions]="[20, 50, 100]" (page)="pageChanged($event)" />
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .page-header { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; margin-bottom: 1rem; }
    h2, p { margin: 0; } p { opacity: .7; margin-top: .2rem; } .spacer { flex: 1 1 auto; }
    .page-header mat-form-field { width: 150px; }
    .page-header .search-field { width: 220px; }
    .create-card { margin-bottom: 1rem; }
    .form-grid { display: grid; grid-template-columns: repeat(3, minmax(160px, 1fr)); gap: .75rem; align-items: center; }
    .form-grid button { justify-self: start; }
    .table-wrap { overflow-x: auto; } table { width: 100%; min-width: 860px; }
    td small { display: block; opacity: .65; }
    td mat-select { min-width: 125px; }
    .status { color: var(--mat-sys-primary); font-weight: 600; } .status.blocked { color: var(--mat-sys-error); }
    @media (max-width: 800px) { .form-grid { grid-template-columns: 1fr; } }
  `,
})
export class AdminUsers {
  private readonly admin = inject(AdminService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly roles: Role[] = ['USER', 'DRIVER', 'DISPATCHER', 'ADMIN'];
  protected readonly columns = ['user', 'role', 'warehouse', 'status', 'action'];
  protected readonly page = signal<Page<User>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected roleFilter: Role | undefined;
  protected statusFilter: 'ACTIVE' | 'BLOCKED' | undefined;
  protected searchFilter = '';
  protected employee = this.emptyEmployee();

  constructor() {
    forkJoin({ users: this.admin.users(0, 20), warehouses: this.admin.warehouses() }).subscribe({
      next: ({ users, warehouses }) => {
        this.page.set(users);
        this.warehouses.set(warehouses);
        this.employee.warehouseId = warehouses[0]?.id ?? 0;
      },
      error: (err) => this.showError(err, 'Не удалось загрузить пользователей'),
    });
  }

  protected load(page: number, size: number): void {
    this.admin.users(page, size, this.roleFilter, this.statusFilter, this.searchFilter.trim()).subscribe({
      next: (result) => this.page.set(result),
      error: (err) => this.showError(err, 'Не удалось загрузить пользователей'),
    });
  }

  protected pageChanged(event: PageEvent): void {
    this.load(event.pageIndex, event.pageSize);
  }

  protected createEmployee(): void {
    this.admin.createEmployee(this.employee).subscribe({
      next: () => {
        this.snackBar.open('Сотрудник создан', 'OK', { duration: 3000 });
        const warehouseId = this.warehouses()[0]?.id ?? 0;
        this.employee = { ...this.emptyEmployee(), warehouseId };
        this.load(0, this.page().size);
      },
      error: (err) => this.showError(err, 'Не удалось создать сотрудника'),
    });
  }

  protected setBlocked(user: User, blocked: boolean): void {
    const request = blocked ? this.admin.blockUser(user.id) : this.admin.unblockUser(user.id);
    request.subscribe({
      next: (updated) => this.replace(updated),
      error: (err) => this.showError(err, 'Не удалось изменить статус'),
    });
  }

  protected changeRole(user: User, role: Role): void {
    const warehouseId = this.isEmployee(role)
      ? user.warehouseId ?? this.warehouses()[0]?.id ?? null
      : null;
    this.admin.changeUserRole(user.id, role, warehouseId).subscribe({
      next: (updated) => this.replace(updated),
      error: (err) => {
        this.showError(err, 'Не удалось изменить роль');
        this.load(this.page().page, this.page().size);
      },
    });
  }

  protected changeWarehouse(user: User, warehouseId: number): void {
    this.admin.changeUserRole(user.id, user.role, warehouseId).subscribe({
      next: (updated) => this.replace(updated),
      error: (err) => {
        this.showError(err, 'Не удалось изменить склад');
        this.load(this.page().page, this.page().size);
      },
    });
  }

  protected isEmployee(role: Role): boolean {
    return role === 'DRIVER' || role === 'DISPATCHER';
  }

  private replace(updated: User): void {
    this.page.update((page) => ({
      ...page,
      content: page.content.map((user) => user.id === updated.id ? updated : user),
    }));
  }

  private emptyEmployee() {
    return {
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      phone: '',
      role: 'DRIVER' as const,
      warehouseId: 0,
    };
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }
}
