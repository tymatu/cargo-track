import { Injectable } from '@angular/core';

const ACCESS_KEY = 'ct_access_token';
const REFRESH_KEY = 'ct_refresh_token';

/**
 * Хранение токенов в localStorage — осознанный трейд-офф MVP
 * (SDP, раздел 5.2). [EXT]: refresh в httpOnly cookie.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  store(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  }

  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }
}
