import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-admin-nav',
  imports: [RouterLink, RouterLinkActive, MatButtonModule, MatIconModule],
  template: `
    <nav aria-label="Разделы администрирования">
      <a matButton routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
        <mat-icon>dashboard</mat-icon> Дашборд
      </a>
      <a matButton routerLink="/admin/users" routerLinkActive="active">
        <mat-icon>group</mat-icon> Пользователи
      </a>
      <a matButton data-testid="admin-nav-shipments" routerLink="/admin/shipments" routerLinkActive="active">
        <mat-icon>route</mat-icon> Рейсы
      </a>
      <a matButton routerLink="/admin/trucks" routerLinkActive="active">
        <mat-icon>local_shipping</mat-icon> Машины
      </a>
      <a matButton routerLink="/admin/warehouses" routerLinkActive="active">
        <mat-icon>warehouse</mat-icon> Склады
      </a>
      <a matButton data-testid="admin-nav-audit" routerLink="/admin/audit" routerLinkActive="active">
        <mat-icon>manage_search</mat-icon> Аудит
      </a>
    </nav>
  `,
  styles: `
    nav {
      display: flex;
      gap: .35rem;
      overflow-x: auto;
      margin-bottom: 1rem;
      padding-bottom: .25rem;
    }
    a { flex: 0 0 auto; }
    .active { background: var(--mat-sys-secondary-container); }
  `,
})
export class AdminNav {}
