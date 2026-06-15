import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User } from '../auth/models';
import {
  CreateShipmentRequest,
  Page,
  Parcel,
  ParcelStatus,
  Shipment,
  ShipmentStatus,
  Truck,
} from './models';

@Injectable({ providedIn: 'root' })
export class DispatcherService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/dispatcher`;

  parcels(
    page: number,
    size: number,
    filters: { status?: ParcelStatus; originWarehouseId?: number; destinationWarehouseId?: number } = {},
  ): Observable<Page<Parcel>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.originWarehouseId) {
      params = params.set('originWarehouseId', filters.originWarehouseId);
    }
    if (filters.destinationWarehouseId) {
      params = params.set('destinationWarehouseId', filters.destinationWarehouseId);
    }
    return this.http.get<Page<Parcel>>(`${this.api}/parcels`, { params });
  }

  acceptParcel(id: number): Observable<Parcel> {
    return this.http.post<Parcel>(`${this.api}/parcels/${id}/accept`, {});
  }

  deliverParcel(id: number): Observable<Parcel> {
    return this.http.post<Parcel>(`${this.api}/parcels/${id}/deliver`, {});
  }

  trucks(): Observable<Truck[]> {
    return this.http.get<Truck[]>(`${this.api}/trucks`);
  }

  drivers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.api}/drivers`);
  }

  createShipment(request: CreateShipmentRequest): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.api}/shipments`, request);
  }

  shipments(page: number, size: number, status?: ShipmentStatus): Observable<Page<Shipment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<Shipment>>(`${this.api}/shipments`, { params });
  }

  shipment(id: number): Observable<Shipment> {
    return this.http.get<Shipment>(`${this.api}/shipments/${id}`);
  }

  loadParcels(id: number, parcelIds: number[]): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.api}/shipments/${id}/parcels`, { parcelIds });
  }

  removeParcel(id: number, parcelId: number): Observable<Shipment> {
    return this.http.delete<Shipment>(`${this.api}/shipments/${id}/parcels/${parcelId}`);
  }
}
