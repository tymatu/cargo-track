import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { forkJoin } from 'rxjs';
import { AdminService } from '../../core/api/admin.service';
import {
  AdminDashboard,
  FleetPosition,
  ParcelStatus,
  STATUS_LABELS,
} from '../../core/api/models';
import { LiveConnectionState, LiveUpdatesService } from '../../core/live/live-updates.service';
import { apiErrorMessage } from '../../shared/api-error';
import { CargoMap } from '../../shared/cargo-map';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    BaseChartDirective,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    AdminNav,
    CargoMap,
  ],
  providers: [provideCharts(withDefaultRegisterables())],
  template: `
    <app-admin-nav />
    <div class="page-header">
      <div>
        <h2 data-testid="admin-dashboard-title">Состояние CargoTrack</h2>
        <p>Оперативные показатели и положение активного флота</p>
      </div>
      <span class="spacer"></span>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Выручка с</mat-label>
        <input matInput type="date" [(ngModel)]="fromDate" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>по</mat-label>
        <input matInput type="date" [(ngModel)]="toDate" />
      </mat-form-field>
      <button matButton="filled" (click)="load()">Обновить</button>
    </div>

    @if (stats(); as value) {
      <section class="stats-grid">
        <mat-card>
          <mat-card-content><mat-icon>group</mat-icon><strong>{{ value.usersTotal }}</strong><span>пользователей</span></mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content><mat-icon>package_2</mat-icon><strong>{{ value.parcelsTotal }}</strong><span>посылок</span></mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content><mat-icon>route</mat-icon><strong>{{ value.shipmentsActive }}</strong><span>активных рейсов</span></mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content><mat-icon>payments</mat-icon><strong>{{ value.revenue | currency: 'CZK' : 'symbol-narrow' : '1.0-0' }}</strong><span>выручка за период</span></mat-card-content>
        </mat-card>
      </section>

      <section class="dashboard-grid">
        <mat-card>
          <mat-card-header><mat-card-title>Посылки по статусам</mat-card-title></mat-card-header>
          <mat-card-content class="chart-wrap">
            <canvas baseChart type="doughnut" [data]="parcelChart()" [options]="chartOptions"></canvas>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-header><mat-card-title>Операции</mat-card-title></mat-card-header>
          <mat-card-content class="metric-list">
            <div><span>Рейсов всего</span><strong>{{ value.shipmentsTotal }}</strong></div>
            <div><span>В пути</span><strong>{{ value.shipmentsInTransit }}</strong></div>
            <div><span>Завершено сегодня</span><strong>{{ value.shipmentsCompletedToday }}</strong></div>
            <div><span>Машин свободно</span><strong>{{ value.trucksIdle }}</strong></div>
            <div><span>На обслуживании</span><strong>{{ value.trucksMaintenance }}</strong></div>
            <small>Период выручки: {{ value.revenueFrom | date: 'dd.MM.yyyy' }}-{{ value.revenueTo | date: 'dd.MM.yyyy' }}</small>
          </mat-card-content>
        </mat-card>
      </section>
    }

    <mat-card class="fleet-card">
      <mat-card-header>
        <mat-card-title>Флот в реальном времени</mat-card-title>
        <span class="spacer"></span>
        <span class="live-pill" [class.connected]="liveState() === 'connected'" [class.connecting]="liveState() === 'connecting'">
          <mat-icon>{{ liveIcons[liveState()] }}</mat-icon>
          {{ liveLabels[liveState()] }}
        </span>
        <span>{{ fleet().length }} машин в пути</span>
      </mat-card-header>
      <mat-card-content>
        <app-cargo-map [fleet]="fleet()" />
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .page-header, mat-card-header { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .page-header { margin-bottom: 1rem; }
    h2, p { margin: 0; }
    p { opacity: .7; margin-top: .2rem; }
    .spacer { flex: 1 1 auto; }
    mat-form-field { width: 160px; }
    .stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; }
    .stats-grid mat-card-content { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: .2rem .75rem; }
    .stats-grid mat-icon { grid-row: 1 / 3; color: var(--mat-sys-primary); }
    .stats-grid strong { font-size: 1.7rem; line-height: 1.1; }
    .stats-grid span { opacity: .7; }
    .dashboard-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 1rem; margin-top: 1rem; }
    .chart-wrap { height: 310px; display: flex; justify-content: center; }
    .metric-list { display: grid; gap: .65rem; }
    .metric-list div { display: flex; justify-content: space-between; border-bottom: 1px solid var(--mat-sys-outline-variant); padding-bottom: .55rem; }
    .metric-list small { opacity: .65; margin-top: .5rem; }
    .fleet-card { margin-top: 1rem; }
    .live-pill {
      display: inline-flex;
      align-items: center;
      gap: .3rem;
      padding: .2rem .55rem;
      border-radius: 999px;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface-variant);
      font-size: .85rem;
      white-space: nowrap;
    }
    .live-pill.connected { color: #1b5e20; background: #e8f5e9; }
    .live-pill.connecting { color: #8a5a00; background: #fff8e1; }
    .live-pill mat-icon { font-size: 18px; width: 18px; height: 18px; }
    @media (max-width: 900px) {
      .stats-grid { grid-template-columns: repeat(2, 1fr); }
      .dashboard-grid { grid-template-columns: 1fr; }
    }
    @media (max-width: 520px) { .stats-grid { grid-template-columns: 1fr; } }
  `,
})
export class AdminDashboardPage {
  private readonly admin = inject(AdminService);
  private readonly live = inject(LiveUpdatesService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly stats = signal<AdminDashboard | null>(null);
  protected readonly fleet = signal<FleetPosition[]>([]);
  protected readonly liveState = this.live.connectionState;
  protected readonly liveLabels: Record<LiveConnectionState, string> = {
    connected: 'Live',
    connecting: 'Подключение',
    disconnected: 'Offline',
  };
  protected readonly liveIcons: Record<LiveConnectionState, string> = {
    connected: 'sensors',
    connecting: 'sync',
    disconnected: 'sensors_off',
  };
  protected fromDate = this.dateInput(new Date(Date.now() - 29 * 86_400_000));
  protected toDate = this.dateInput(new Date());
  protected readonly chartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'right' } },
  };
  protected readonly parcelChart = computed<ChartData<'doughnut'>>(() => {
    const counts = this.stats()?.parcelsByStatus;
    const statuses = Object.keys(STATUS_LABELS) as ParcelStatus[];
    return {
      labels: statuses.map((status) => STATUS_LABELS[status]),
      datasets: [{
        data: statuses.map((status) => counts?.[status] ?? 0),
        backgroundColor: ['#90caf9', '#42a5f5', '#7e57c2', '#ffb74d', '#66bb6a', '#26a69a', '#ef5350'],
      }],
    };
  });

  constructor() {
    this.load();
    this.live.watch<FleetPosition[] | FleetPosition>('/topic/admin/fleet')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (update) => Array.isArray(update) ? this.fleet.set(update) : this.upsertFleet(update),
        error: (err) => this.showError(err, 'Live-обновления флота временно недоступны'),
      });
  }

  protected load(): void {
    const period = this.dashboardPeriod();
    if (!period) {
      this.snackBar.open('Укажите корректный период выручки', 'OK', { duration: 5000 });
      return;
    }
    forkJoin({
      stats: this.admin.dashboard(period.from, period.to),
      fleet: this.admin.fleet(),
    }).subscribe({
      next: ({ stats, fleet }) => {
        this.stats.set(stats);
        this.fleet.set(fleet);
      },
      error: (err) => this.showError(err, 'Не удалось загрузить дашборд'),
    });
  }

  private upsertFleet(update: FleetPosition): void {
    this.fleet.update((items) => {
      const index = items.findIndex((item) => item.truckId === update.truckId);
      if (update.shipmentStatus !== 'IN_TRANSIT') {
        return items.filter((item) => item.truckId !== update.truckId);
      }
      if (index < 0) return [...items, update];
      return items.map((item, current) => current === index ? update : item);
    });
  }

  private dateInput(date: Date): string {
    return date.toISOString().slice(0, 10);
  }

  private dashboardPeriod(): { from: string; to: string } | null {
    const from = this.dateAtStartOfDay(this.fromDate);
    const to = this.dateAtStartOfDay(this.toDate);
    if (!from || !to) {
      return null;
    }
    to.setDate(to.getDate() + 1);
    if (from >= to) {
      return null;
    }
    return { from: from.toISOString(), to: to.toISOString() };
  }

  private dateAtStartOfDay(value: string): Date | null {
    if (!value) {
      return null;
    }
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(apiErrorMessage(err, fallback), 'OK', { duration: 5000 });
  }
}
