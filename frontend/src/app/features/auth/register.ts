import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-register',
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
          <mat-card-title>Регистрация</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Имя</mat-label>
              <input matInput formControlName="firstName" autocomplete="given-name" />
              @if (form.controls.firstName.hasError('required')) {
                <mat-error>Укажите имя</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Фамилия</mat-label>
              <input matInput formControlName="lastName" autocomplete="family-name" />
              @if (form.controls.lastName.hasError('required')) {
                <mat-error>Укажите фамилию</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" />
              @if (form.controls.email.hasError('required')) {
                <mat-error>Укажите email</mat-error>
              } @else if (form.controls.email.hasError('email')) {
                <mat-error>Некорректный email</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Телефон (необязательно)</mat-label>
              <input matInput formControlName="phone" autocomplete="tel" />
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Пароль</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password" />
              @if (form.controls.password.hasError('required')) {
                <mat-error>Укажите пароль</mat-error>
              } @else if (form.controls.password.hasError('minlength')) {
                <mat-error>Минимум 8 символов</mat-error>
              }
            </mat-form-field>

            @if (error()) {
              <p class="auth-error" role="alert">{{ error() }}</p>
            }

            <button matButton="filled" type="submit" class="full-width" [disabled]="loading()">
              {{ loading() ? 'Создаём аккаунт…' : 'Зарегистрироваться' }}
            </button>
          </form>
        </mat-card-content>
        <mat-card-footer>
          <p class="auth-switch">Уже есть аккаунт? <a routerLink="/login">Войти</a></p>
        </mat-card-footer>
      </mat-card>
    </div>
  `,
  styleUrl: './auth.scss',
})
export class Register {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const { phone, ...rest } = this.form.getRawValue();
    this.auth.register({ ...rest, phone: phone || undefined }).subscribe({
      next: () => {
        this.snackBar.open('Аккаунт создан — теперь войдите', 'OK', { duration: 5000 });
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.detail ?? 'Не удалось зарегистрироваться.');
      },
    });
  }
}
