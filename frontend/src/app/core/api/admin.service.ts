import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Role, User } from '../auth/models';
import {
  AdminDashboard,
  AuditLog,
  CreateEmployeeRequest,
  FleetPosition,
  Page,
  Shipment,
  ShipmentStatus,
  Truck,
  TruckRequest,
  Warehouse,
  WarehouseRequest,
} from './models';

export interface AuditFilters {
  userId?: number;
  action?: string;
  entityType?: string;
  entityId?: number;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/admin`;

  dashboard(from?: string, to?: string): Observable<AdminDashboard> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<AdminDashboard>(`${this.api}/stats/dashboard`, { params });
  }

  fleet(): Observable<FleetPosition[]> {
    return this.http.get<FleetPosition[]>(`${this.api}/fleet`);
  }

  shipments(
    page = 0,
    size = 20,
    status?: ShipmentStatus,
    warehouseId?: number,
    driverId?: number,
  ): Observable<Page<Shipment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (driverId) params = params.set('driverId', driverId);
    return this.http.get<Page<Shipment>>(`${this.api}/shipments`, { params });
  }

  users(
    page = 0,
    size = 50,
    role?: Role,
    status?: 'ACTIVE' | 'BLOCKED',
    search?: string,
  ): Observable<Page<User>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (role) params = params.set('role', role);
    if (status) params = params.set('status', status);
    if (search) params = params.set('search', search);
    return this.http.get<Page<User>>(`${this.api}/users`, { params });
  }

  createEmployee(request: CreateEmployeeRequest): Observable<User> {
    return this.http.post<User>(`${this.api}/users`, request);
  }

  blockUser(id: number): Observable<User> {
    return this.http.post<User>(`${this.api}/users/${id}/block`, {});
  }

  unblockUser(id: number): Observable<User> {
    return this.http.post<User>(`${this.api}/users/${id}/unblock`, {});
  }

  changeUserRole(id: number, role: Role, warehouseId: number | null): Observable<User> {
    return this.http.patch<User>(`${this.api}/users/${id}/role`, { role, warehouseId });
  }

  trucks(): Observable<Truck[]> {
    return this.http.get<Truck[]>(`${this.api}/trucks`);
  }

  createTruck(request: TruckRequest): Observable<Truck> {
    return this.http.post<Truck>(`${this.api}/trucks`, request);
  }

  updateTruck(id: number, request: TruckRequest): Observable<Truck> {
    return this.http.put<Truck>(`${this.api}/trucks/${id}`, request);
  }

  deleteTruck(id: number): Observable<Truck> {
    return this.http.delete<Truck>(`${this.api}/trucks/${id}`);
  }

  warehouses(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(`${this.api}/warehouses`);
  }

  createWarehouse(request: WarehouseRequest): Observable<Warehouse> {
    return this.http.post<Warehouse>(`${this.api}/warehouses`, request);
  }

  updateWarehouse(id: number, request: WarehouseRequest): Observable<Warehouse> {
    return this.http.put<Warehouse>(`${this.api}/warehouses/${id}`, request);
  }

  deleteWarehouse(id: number): Observable<Warehouse> {
    return this.http.delete<Warehouse>(`${this.api}/warehouses/${id}`);
  }

  audit(page: number, size: number, filters: AuditFilters): Observable<Page<AuditLog>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<AuditLog>>(`${this.api}/audit`, { params });
  }
}
