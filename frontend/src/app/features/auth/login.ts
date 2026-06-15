import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../shared/api-error';

interface DemoAccount {
  readonly id: string;
  readonly label: string;
  readonly email: string;
}

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Вход в CargoTrack</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput data-testid="login-email" type="email" formControlName="email" autocomplete="email" />
              @if (form.controls.email.hasError('required')) {
                <mat-error>Укажите email</mat-error>
              } @else if (form.controls.email.hasError('email')) {
                <mat-error>Некорректный email</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Пароль</mat-label>
              <input matInput data-testid="login-password" type="password" formControlName="password" autocomplete="current-password" />
              @if (form.controls.password.hasError('required')) {
                <mat-error>Укажите пароль</mat-error>
              }
            </mat-form-field>

            @if (error()) {
              <p class="auth-error" role="alert">{{ error() }}</p>
            }

            <button matButton="filled" data-testid="login-submit" type="submit" class="full-width" [disabled]="loading()">
              {{ loading() ? 'Входим…' : 'Войти' }}
            </button>
          </form>

          <div class="demo-divider"><span>Демо-вход в один клик</span></div>
          <div class="demo-grid">
            @for (account of demoAccounts; track account.email) {
              <button
                matButton="outlined"
                type="button"
                class="demo-account"
                [attr.data-testid]="'demo-login-' + account.id"
                [disabled]="loading()"
                (click)="loginAs(account)"
              >
                <strong>{{ account.label }}</strong>
                <span>{{ account.email }}</span>
              </button>
            }
          </div>
          <p class="demo-password">Общий пароль: <code>CargoTrack123!</code></p>
        </mat-card-content>
        <mat-card-footer>
          <p class="auth-switch">Нет аккаунта? <a routerLink="/register">Зарегистрироваться</a></p>
        </mat-card-footer>
      </mat-card>
    </div>
  `,
  styleUrl: './auth.scss',
})
export class Login {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly demoAccounts: readonly DemoAccount[] = [
    { id: 'user', label: 'Клиент', email: 'user@cargotrack.local' },
    { id: 'driver', label: 'Водитель', email: 'driver.prague@cargotrack.local' },
    { id: 'dispatcher', label: 'Диспетчер', email: 'dispatcher.prague@cargotrack.local' },
    { id: 'admin', label: 'Администратор', email: 'admin@cargotrack.local' },
  ];

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Не удалось войти. Попробуйте ещё раз.'));
      },
    });
  }

  protected loginAs(account: DemoAccount): void {
    this.form.setValue({
      email: account.email,
      password: 'CargoTrack123!',
    });
    this.submit();
  }
}
