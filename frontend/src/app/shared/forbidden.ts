import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-forbidden',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  template: `
    <mat-card class="forbidden">
      <mat-card-header>
        <mat-card-title>403 — доступ запрещён</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>У вашей роли нет доступа к этому разделу.</p>
        <a matButton routerLink="/">На главную</a>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .forbidden {
      max-width: 480px;
      margin: 2rem auto;
    }
  `,
})
export class Forbidden {}
