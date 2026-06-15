import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page, Shipment, ShipmentStatus } from './models';

@Injectable({ providedIn: 'root' })
export class DriverService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/driver`;

  myShipments(
    page: number,
    size: number,
    status?: ShipmentStatus,
  ): Observable<Page<Shipment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<Shipment>>(`${this.api}/shipments/my`, { params });
  }

  shipment(id: number): Observable<Shipment> {
    return this.http.get<Shipment>(`${this.api}/shipments/${id}`);
  }

  depart(id: number): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.api}/shipments/${id}/depart`, {});
  }

  arrive(id: number): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.api}/shipments/${id}/arrive`, {});
  }
}
