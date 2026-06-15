import { Injectable } from '@angular/core';

const LEGACY_ACCESS_KEY = 'ct_access_token';
const LEGACY_REFRESH_KEY = 'ct_refresh_token';

@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private accessTokenValue: string | null = null;

  constructor() {
    localStorage.removeItem(LEGACY_ACCESS_KEY);
    localStorage.removeItem(LEGACY_REFRESH_KEY);
  }

  get accessToken(): string | null {
    return this.accessTokenValue;
  }

  store(accessToken: string): void {
    this.accessTokenValue = accessToken;
  }

  clear(): void {
    this.accessTokenValue = null;
    localStorage.removeItem(LEGACY_ACCESS_KEY);
    localStorage.removeItem(LEGACY_REFRESH_KEY);
  }
}
