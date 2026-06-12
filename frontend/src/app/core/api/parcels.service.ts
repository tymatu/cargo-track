import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateParcelRequest,
  Page,
  Parcel,
  ParcelDetail,
  ParcelStatus,
  PriceQuote,
} from './models';

@Injectable({ providedIn: 'root' })
export class ParcelsService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/parcels`;

  my(page: number, size: number, status: ParcelStatus | null): Observable<Page<Parcel>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<Parcel>>(`${this.api}/my`, { params });
  }

  detail(id: number): Observable<ParcelDetail> {
    return this.http.get<ParcelDetail>(`${this.api}/${id}`);
  }

  create(request: CreateParcelRequest): Observable<Parcel> {
    return this.http.post<Parcel>(this.api, request);
  }

  calculatePrice(request: Partial<CreateParcelRequest>): Observable<PriceQuote> {
    return this.http.post<PriceQuote>(`${this.api}/calculate-price`, request);
  }

  cancel(id: number): Observable<Parcel> {
    return this.http.post<Parcel>(`${this.api}/${id}/cancel`, {});
  }
}
