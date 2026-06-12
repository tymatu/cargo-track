import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, Subject, catchError, switchMap, take, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

/** Эндпоинты, где Bearer не нужен и 401 — это ответ бизнес-логики, а не сигнал к refresh. */
const SKIP_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

let refreshing = false;
const refreshed$ = new Subject<string | null>();

/**
 * Подставляет Authorization: Bearer; на 401 делает ОДИН refresh,
 * остальные запросы ждут его в очереди (SDP, раздел 10.2).
 * Если refresh провалился — logout.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorage);
  const auth = inject(AuthService);

  if (SKIP_PATHS.some((path) => req.url.includes(path))) {
    return next(req);
  }

  const accessToken = storage.accessToken;
  const authorized = accessToken ? withToken(req, accessToken) : req;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || !storage.refreshToken) {
        return throwError(() => error);
      }
      return handle401(req, next, auth);
    }),
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
): Observable<HttpEvent<unknown>> {
  if (!refreshing) {
    refreshing = true;
    return auth.refresh().pipe(
      switchMap((res) => {
        refreshing = false;
        refreshed$.next(res.accessToken);
        return next(withToken(req, res.accessToken));
      }),
      catchError((refreshError) => {
        refreshing = false;
        refreshed$.next(null);
        auth.forceLogout();
        return throwError(() => refreshError);
      }),
    );
  }

  // refresh уже идёт — ждём его результат и повторяем запрос с новым токеном
  return refreshed$.pipe(
    take(1),
    switchMap((token) =>
      token
        ? next(withToken(req, token))
        : throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' })),
    ),
  );
}

function withToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
