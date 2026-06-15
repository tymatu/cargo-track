import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LiveUpdatesService } from '../live/live-updates.service';
import { SKIP_AUTH_REFRESH } from './auth-context';
import { AuthResponse, RegisterRequest, Role, User } from './models';
import { TokenStorage } from './token-storage';

const HOME_BY_ROLE: Record<Role, string> = {
  USER: '/parcels',
  DRIVER: '/driver',
  DISPATCHER: '/dispatcher',
  ADMIN: '/admin',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);
  private readonly liveUpdates = inject(LiveUpdatesService);
  private readonly api = `${environment.apiUrl}/auth`;

  readonly currentUser = signal<User | null>(null);
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.api}/register`, {
      ...request,
      email: request.email.trim().toLowerCase(),
    });
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(
        `${this.api}/login`,
        { email: email.trim().toLowerCase(), password },
        { withCredentials: true },
      )
      .pipe(tap((res) => this.applyAuth(res)));
  }

  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/refresh`, {}, { withCredentials: true })
      .pipe(tap((res) => this.applyAuth(res)));
  }

  logout(): void {
    const context = new HttpContext().set(SKIP_AUTH_REFRESH, true);
    this.http
      .post(`${this.api}/logout`, {}, { withCredentials: true, context })
      .pipe(finalize(() => this.forceLogout()))
      .subscribe();
  }

  forceLogout(): void {
    this.liveUpdates.disconnect();
    this.storage.clear();
    this.clearSessionCookie();
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  async restoreSession(): Promise<void> {
    if (!this.hasSessionCookie()) {
      return;
    }
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.liveUpdates.disconnect();
      this.storage.clear();
      this.clearSessionCookie();
      this.currentUser.set(null);
    }
  }

  hasAnyRole(roles: Role[]): boolean {
    const user = this.currentUser();
    return user !== null && roles.includes(user.role);
  }

  homePath(): string {
    const user = this.currentUser();
    return user ? HOME_BY_ROLE[user.role] : '/login';
  }

  private applyAuth(res: AuthResponse): void {
    this.storage.store(res.accessToken);
    this.currentUser.set(res.user);
  }

  private hasSessionCookie(): boolean {
    return (
      typeof document !== 'undefined' &&
      document.cookie.split(';').some((cookie) => cookie.trim() === 'ct_session=1')
    );
  }

  private clearSessionCookie(): void {
    if (typeof document !== 'undefined') {
      document.cookie = 'ct_session=; Max-Age=0; path=/';
    }
  }
}
