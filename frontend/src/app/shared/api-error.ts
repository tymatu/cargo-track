import { HttpErrorResponse } from '@angular/common/http';

export function apiErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const detail = detailFromBody(err.error);
    if (detail) {
      return detail;
    }
  }
  return fallback;
}

function detailFromBody(body: unknown): string | null {
  if (typeof body === 'object' && body !== null && 'detail' in body) {
    const detail = (body as { detail?: unknown }).detail;
    return typeof detail === 'string' && detail.trim() ? detail : null;
  }
  return null;
}
