import { Injectable, inject, signal } from '@angular/core';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenStorage } from '../auth/token-storage';

export type LiveConnectionState = 'connected' | 'connecting' | 'disconnected';

@Injectable({ providedIn: 'root' })
export class LiveUpdatesService {
  private readonly storage = inject(TokenStorage);
  private readonly client = new RxStomp();

  readonly connectionState = signal<LiveConnectionState>('disconnected');

  constructor() {
    this.client.configure({
      brokerURL: environment.wsUrl,
      reconnectDelay: 2000,
      connectionTimeout: 8000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: (client) => {
        const token = this.storage.accessToken;
        if (!token) {
          throw new Error('Access token is required for live updates');
        }
        client.configure({
          connectHeaders: { Authorization: `Bearer ${token}` },
        });
      },
    });

    this.client.connectionState$.subscribe((state) => {
      this.connectionState.set(this.toConnectionState(state));
    });
  }

  watch<T>(destination: string): Observable<T> {
    if (!this.client.active) {
      this.client.activate();
    }
    return this.client
      .watch(destination)
      .pipe(map((message) => JSON.parse(message.body) as T));
  }

  disconnect(): void {
    if (this.client.active) {
      void this.client.deactivate({ force: true });
    }
    this.connectionState.set('disconnected');
  }

  private toConnectionState(state: RxStompState): LiveConnectionState {
    if (state === RxStompState.OPEN) {
      return 'connected';
    }
    if (state === RxStompState.CONNECTING || state === RxStompState.CLOSING) {
      return 'connecting';
    }
    return 'disconnected';
  }
}
