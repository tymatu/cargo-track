export type Role = 'USER' | 'DRIVER' | 'DISPATCHER' | 'ADMIN';

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: Role;
  status: 'ACTIVE' | 'BLOCKED';
  warehouseId: number | null;
}

export interface AuthResponse {
  accessToken: string;
  user: User;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}
