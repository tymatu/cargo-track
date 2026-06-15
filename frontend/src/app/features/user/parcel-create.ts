import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { Router } from '@angular/router';
import { merge } from 'rxjs';
import { CreateParcelRequest, PriceQuote } from '../../core/api/models';
import { ParcelsService } from '../../core/api/parcels.service';
import { WarehousesService } from '../../core/api/warehouses.service';
import { apiErrorMessage } from '../../shared/api-error';

/** Stepper создания посылки: маршрут и получатель → габариты → подтверждение с ценой (SDP, 10.2). */
@Component({
  selector: 'app-parcel-create',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatStepperModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <mat-card class="create-card">
      <mat-card-header>
        <mat-card-title>Новая посылка</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <mat-stepper linear #stepper>
          <!-- Шаг 1: маршрут и получатель -->
          <mat-step [stepControl]="routeForm" label="Маршрут и получатель">
            <form [formGroup]="routeForm" class="step-form">
              <mat-form-field appearance="outline">
                <mat-label>Склад отправления</mat-label>
                <mat-select formControlName="originWarehouseId">
                  @for (w of warehouses(); track w.id) {
                    <mat-option [value]="w.id">{{ w.city }} — {{ w.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Склад назначения</mat-label>
                <mat-select formControlName="destinationWarehouseId">
                  @for (w of warehouses(); track w.id) {
                    <mat-option [value]="w.id" [disabled]="w.id === routeForm.value.originWarehouseId">
                      {{ w.city }} — {{ w.name }}
                    </mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Имя получателя</mat-label>
                <input matInput formControlName="recipientName" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Телефон получателя</mat-label>
                <input matInput formControlName="recipientPhone" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Email получателя (необязательно)</mat-label>
                <input matInput type="email" formControlName="recipientEmail" />
              </mat-form-field>
              <div class="step-actions">
                <button matButton="filled" matStepperNext type="button" [disabled]="routeForm.invalid">
                  Далее
                </button>
              </div>
            </form>
          </mat-step>

          <!-- Шаг 2: вес и габариты -->
          <mat-step [stepControl]="sizeForm" label="Вес и габариты">
            <form [formGroup]="sizeForm" class="step-form">
              <mat-form-field appearance="outline">
                <mat-label>Вес, кг</mat-label>
                <input matInput type="number" formControlName="weightKg" min="0.1" step="0.1" />
              </mat-form-field>
              <div class="dimensions">
                <mat-form-field appearance="outline">
                  <mat-label>Длина, см</mat-label>
                  <input matInput type="number" formControlName="lengthCm" min="1" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Ширина, см</mat-label>
                  <input matInput type="number" formControlName="widthCm" min="1" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Высота, см</mat-label>
                  <input matInput type="number" formControlName="heightCm" min="1" />
                </mat-form-field>
              </div>
              <mat-form-field appearance="outline">
                <mat-label>Объявленная ценность, € (необязательно)</mat-label>
                <input matInput type="number" formControlName="declaredValue" min="0" />
              </mat-form-field>
              <div class="step-actions">
                <button matButton matStepperPrevious type="button">Назад</button>
                <button matButton="filled" matStepperNext type="button"
                        [disabled]="sizeForm.invalid" (click)="fetchQuote()">
                  Рассчитать цену
                </button>
              </div>
            </form>
          </mat-step>

          <!-- Шаг 3: подтверждение -->
          <mat-step label="Подтверждение">
            @if (quote(); as q) {
              <div class="quote">
                <p>Тарифный вес: <strong>{{ q.chargeableWeightKg | number: '1.0-2' }} кг</strong></p>
                <p>Расстояние: <strong>{{ q.distanceKm | number: '1.0-1' }} км</strong></p>
                <p class="quote-price">Итого: {{ q.price | number: '1.2-2' }} €</p>
              </div>
            } @else {
              <p>Рассчитываем цену…</p>
            }
            @if (error()) {
              <p class="form-error" role="alert">{{ error() }}</p>
            }
            <div class="step-actions">
              <button matButton matStepperPrevious type="button">Назад</button>
              <button matButton="filled" type="button"
                      [disabled]="submitting() || !quote()" (click)="submit()">
                {{ submitting() ? 'Создаём…' : 'Создать посылку' }}
              </button>
            </div>
          </mat-step>
        </mat-stepper>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .create-card { max-width: 720px; margin: 0 auto; }
    .step-form { display: flex; flex-direction: column; padding-top: 1rem; }
    .dimensions { display: flex; gap: 0.75rem; flex-wrap: wrap; }
    .dimensions mat-form-field { flex: 1 1 120px; }
    .step-actions { display: flex; gap: 0.75rem; margin-top: 0.5rem; }
    .quote-price { font-size: 1.4rem; font-weight: 600; }
    .form-error { color: var(--mat-sys-error, #b3261e); }
  `,
})
export class ParcelCreate {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly parcels = inject(ParcelsService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly warehouses = toSignal(inject(WarehousesService).warehouses$, {
    initialValue: [],
  });

  protected readonly quote = signal<PriceQuote | null>(null);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly routeForm = this.fb.group({
    originWarehouseId: [null as number | null, Validators.required],
    destinationWarehouseId: [null as number | null, Validators.required],
    recipientName: ['', Validators.required],
    recipientPhone: ['', Validators.required],
    recipientEmail: ['', Validators.email],
  });

  protected readonly sizeForm = this.fb.group({
    weightKg: [null as number | null, [Validators.required, Validators.min(0.1)]],
    lengthCm: [null as number | null, Validators.min(1)],
    widthCm: [null as number | null, Validators.min(1)],
    heightCm: [null as number | null, Validators.min(1)],
    declaredValue: [null as number | null, Validators.min(0)],
  });

  constructor() {
    merge(
      this.routeForm.controls.originWarehouseId.valueChanges,
      this.routeForm.controls.destinationWarehouseId.valueChanges,
      this.sizeForm.valueChanges,
    ).pipe(takeUntilDestroyed()).subscribe(() => {
      this.quote.set(null);
      this.error.set(null);
    });

    this.routeForm.controls.originWarehouseId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((originWarehouseId) => {
        if (originWarehouseId === this.routeForm.controls.destinationWarehouseId.value) {
          this.routeForm.controls.destinationWarehouseId.reset();
        }
      });
  }

  protected fetchQuote(): void {
    this.quote.set(null);
    this.error.set(null);
    const route = this.routeForm.getRawValue();
    const size = this.sizeForm.getRawValue();
    if (
      this.routeForm.invalid ||
      this.sizeForm.invalid ||
      route.originWarehouseId === null ||
      route.destinationWarehouseId === null ||
      size.weightKg === null
    ) {
      this.error.set('Заполните маршрут, получателя и вес посылки');
      return;
    }
    const request = {
      originWarehouseId: route.originWarehouseId,
      destinationWarehouseId: route.destinationWarehouseId,
      weightKg: size.weightKg,
      lengthCm: size.lengthCm ?? undefined,
      widthCm: size.widthCm ?? undefined,
      heightCm: size.heightCm ?? undefined,
    };
    this.parcels
      .calculatePrice(request)
      .subscribe({
        next: (quote) => {
          if (this.matchesQuoteRequest(request)) {
            this.quote.set(quote);
          }
        },
        error: (err) => this.error.set(apiErrorMessage(err, 'Не удалось рассчитать цену')),
      });
  }

  protected submit(): void {
    if (this.submitting() || !this.quote()) {
      return;
    }
    this.error.set(null);
    const route = this.routeForm.getRawValue();
    const size = this.sizeForm.getRawValue();
    if (
      this.routeForm.invalid ||
      this.sizeForm.invalid ||
      route.originWarehouseId === null ||
      route.destinationWarehouseId === null ||
      size.weightKg === null
    ) {
      this.error.set('Заполните маршрут, получателя и вес посылки');
      return;
    }
    this.submitting.set(true);
    const request: CreateParcelRequest = {
      originWarehouseId: route.originWarehouseId,
      destinationWarehouseId: route.destinationWarehouseId,
      recipientName: route.recipientName,
      recipientPhone: route.recipientPhone,
      recipientEmail: route.recipientEmail || undefined,
      weightKg: size.weightKg,
      lengthCm: size.lengthCm ?? undefined,
      widthCm: size.widthCm ?? undefined,
      heightCm: size.heightCm ?? undefined,
      declaredValue: size.declaredValue ?? undefined,
    };
    this.parcels.create(request).subscribe({
      next: (parcel) => {
        this.snackBar.open(`Посылка создана: ${parcel.trackingNumber}`, 'OK', { duration: 7000 });
        this.router.navigate(['/parcels', parcel.id]);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(apiErrorMessage(err, 'Не удалось создать посылку'));
      },
    });
  }

  private matchesQuoteRequest(request: {
    originWarehouseId: number;
    destinationWarehouseId: number;
    weightKg: number;
    lengthCm?: number;
    widthCm?: number;
    heightCm?: number;
  }): boolean {
    return this.routeForm.value.originWarehouseId === request.originWarehouseId
      && this.routeForm.value.destinationWarehouseId === request.destinationWarehouseId
      && this.sizeForm.value.weightKg === request.weightKg
      && (this.sizeForm.value.lengthCm ?? undefined) === request.lengthCm
      && (this.sizeForm.value.widthCm ?? undefined) === request.widthCm
      && (this.sizeForm.value.heightCm ?? undefined) === request.heightCm;
  }
}
