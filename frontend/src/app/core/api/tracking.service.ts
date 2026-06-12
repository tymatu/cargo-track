import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PublicTracking } from './models';

@Injectable({ providedIn: 'root' })
export class TrackingService {
  private readonly http = inject(HttpClient);

  track(trackingNumber: string): Observable<PublicTracking> {
    return this.http.get<PublicTracking>(
      `${environment.apiUrl}/tracking/${encodeURIComponent(trackingNumber.trim())}`,
    );
  }
}
