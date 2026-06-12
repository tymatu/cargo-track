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

export interface ParcelDetail {
  parcel: Parcel;
  events: TrackingEvent[];
}

export interface PublicTracking {
  trackingNumber: string;
  status: ParcelStatus;
  originCity: string;
  destinationCity: string;
  recipientNameMasked: string;
  createdAt: string;
  events: TrackingEvent[];
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

export const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: 'Создана',
  ACCEPTED_AT_ORIGIN: 'Принята на складе',
  LOADED: 'Загружена в рейс',
  IN_TRANSIT: 'В пути',
  ARRIVED_AT_DESTINATION: 'Прибыла на склад назначения',
  DELIVERED: 'Доставлена',
  CANCELLED: 'Отменена',
};
