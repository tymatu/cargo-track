import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';

/** Заглушка раздела: реальные страницы появляются в следующих фазах SDP. */
@Component({
  selector: 'app-coming-soon',
  imports: [MatCardModule],
  template: `
    <mat-card class="coming-soon">
      <mat-card-header>
        <mat-card-title>{{ title }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>Раздел в разработке — появится в одной из следующих фаз.</p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .coming-soon {
      max-width: 480px;
      margin: 2rem auto;
    }
  `,
})
export class ComingSoon {
  protected readonly title: string =
    inject(ActivatedRoute).snapshot.data['title'] ?? 'Раздел';
}
