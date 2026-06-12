import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Warehouse } from './models';

@Injectable({ providedIn: 'root' })
export class WarehousesService {
  private readonly http = inject(HttpClient);

  /** Справочник меняется редко — кэшируем на время сессии. */
  readonly warehouses$: Observable<Warehouse[]> = this.http
    .get<Warehouse[]>(`${environment.apiUrl}/warehouses`)
    .pipe(shareReplay(1));
}
