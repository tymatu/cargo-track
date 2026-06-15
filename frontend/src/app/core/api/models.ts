import type { Role, User } from '../auth/models';

export interface Warehouse {
  id: number;
  name: string;
  city: string;
  address: string;
  latitude: number;
  longitude: number;
}

export type ParcelStatus =
  | 'CREATED'
  | 'ACCEPTED_AT_ORIGIN'
  | 'LOADED'
  | 'IN_TRANSIT'
  | 'ARRIVED_AT_DESTINATION'
  | 'DELIVERED'
  | 'CANCELLED';

export interface Parcel {
  id: number;
  trackingNumber: string;
  status: ParcelStatus;
  originWarehouse: Warehouse;
  destinationWarehouse: Warehouse;
  recipientName: string;
  recipientPhone: string;
  recipientEmail: string | null;
  weightKg: number;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  declaredValue: number | null;
  price: number;
  createdAt: string;
}

export interface TrackingEvent {
  status: ParcelStatus;
  description: string | null;
  warehouseCity: string | null;
  createdAt: string;
}

export interface RoutePoint {
  latitude: number;
  longitude: number;
}

export interface ShipmentRoute {
  distanceKm: number;
  durationMin: number;
  geometry: RoutePoint[];
  source: 'OSRM' | 'CACHE' | 'FALLBACK';
}

export interface TruckPosition {
  latitude: number;
  longitude: number;
  bearing: number;
  recordedAt: string | null;
}

export interface TrackingMap {
  shipmentId: number;
  truckId: number;
  route: ShipmentRoute | null;
  position: TruckPosition | null;
}

export interface PublicTrackingMap {
  progressPercent: number;
  updatedAt: string | null;
}

export interface ShipmentLiveUpdate {
  shipmentId: number;
  status: ShipmentStatus;
  truckStatus: TruckStatus;
  position: TruckPosition | null;
  arrivedAt: string | null;
}

export interface ParcelLiveUpdate {
  parcelId: number;
  status: ParcelStatus;
  event: TrackingEvent;
}

export interface FleetPosition {
  shipmentId: number;
  truckId: number;
  plateNumber: string;
  truckStatus: TruckStatus;
  shipmentStatus: ShipmentStatus;
  position: TruckPosition | null;
}

export interface ParcelDetail {
  parcel: Parcel;
  events: TrackingEvent[];
  tracking: TrackingMap | null;
}

export interface PublicTracking {
  trackingNumber: string;
  status: ParcelStatus;
  originCity: string;
  destinationCity: string;
  recipientNameMasked: string;
  createdAt: string;
  events: TrackingEvent[];
  tracking: PublicTrackingMap | null;
}

export interface PriceQuote {
  price: number;
  chargeableWeightKg: number;
  distanceKm: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateParcelRequest {
  originWarehouseId: number;
  destinationWarehouseId: number;
  recipientName: string;
  recipientPhone: string;
  recipientEmail?: string;
  weightKg: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  declaredValue?: number;
}

export type TruckStatus = 'IDLE' | 'IN_TRANSIT' | 'MAINTENANCE';

export interface Truck {
  id: number;
  plateNumber: string;
  model: string;
  capacityKg: number;
  status: TruckStatus;
  homeWarehouseId: number;
}

export type ShipmentStatus =
  | 'PLANNED'
  | 'LOADING'
  | 'IN_TRANSIT'
  | 'COMPLETED'
  | 'CANCELLED';

export interface ShipmentParcel {
  id: number;
  trackingNumber: string;
  status: ParcelStatus;
  weightKg: number;
  loadedAt: string;
}

export interface Shipment {
  id: number;
  status: ShipmentStatus;
  truck: Truck;
  driver: User;
  originWarehouse: Warehouse;
  destinationWarehouse: Warehouse;
  plannedDepartureAt: string | null;
  departedAt: string | null;
  arrivedAt: string | null;
  loadedWeightKg: number;
  parcels: ShipmentParcel[];
  route: ShipmentRoute | null;
  position: TruckPosition | null;
  createdAt: string;
}

export interface CreateShipmentRequest {
  truckId: number;
  driverId: number;
  destinationWarehouseId: number;
  plannedDepartureAt?: string;
}

export interface AdminDashboard {
  usersTotal: number;
  usersActive: number;
  parcelsTotal: number;
  parcelsByStatus: Record<ParcelStatus, number>;
  revenue: number;
  revenueFrom: string;
  revenueTo: string;
  shipmentsTotal: number;
  shipmentsActive: number;
  shipmentsInTransit: number;
  shipmentsCompletedToday: number;
  trucksTotal: number;
  trucksIdle: number;
  trucksInTransit: number;
  trucksMaintenance: number;
}

export interface TruckRequest {
  plateNumber: string;
  model: string;
  capacityKg: number;
  status: TruckStatus;
  homeWarehouseId: number;
}

export interface WarehouseRequest {
  name: string;
  city: string;
  address: string;
  latitude: number;
  longitude: number;
}

export interface CreateEmployeeRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role: Extract<Role, 'DRIVER' | 'DISPATCHER'>;
  warehouseId: number;
}

export interface AuditLog {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  entityType: string | null;
  entityId: number | null;
  oldValue: unknown;
  newValue: unknown;
  ipAddress: string | null;
  userAgent: string | null;
  httpMethod: string | null;
  endpoint: string | null;
  createdAt: string;
}

export const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: 'Создана',
  ACCEPTED_AT_ORIGIN: 'Принята на складе',
  LOADED: 'Загружена в рейс',
  IN_TRANSIT: 'В пути',
  ARRIVED_AT_DESTINATION: 'Прибыла на склад назначения',
  DELIVERED: 'Доставлена',
  CANCELLED: 'Отменена',
};

export const SHIPMENT_STATUS_LABELS: Record<ShipmentStatus, string> = {
  PLANNED: 'Запланирован',
  LOADING: 'Погрузка',
  IN_TRANSIT: 'В пути',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
};
