import { Component, ElementRef, OnDestroy, afterNextRender, effect, input, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { FleetPosition, RoutePoint, TruckPosition } from '../core/api/models';

@Component({
  selector: 'app-cargo-map',
  template: `<div #map class="map" aria-label="Карта маршрута"></div>`,
  styles: `
    :host { display: block; }
    .map {
      width: 100%;
      height: 360px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container);
    }
    @media (max-width: 600px) {
      .map { height: 300px; }
    }
  `,
})
export class CargoMap implements OnDestroy {
  readonly route = input<RoutePoint[]>([]);
  readonly position = input<TruckPosition | null>(null);
  readonly fleet = input<FleetPosition[]>([]);

  private readonly mapElement = viewChild.required<ElementRef<HTMLDivElement>>('map');
  private map?: L.Map;
  private routeLayer?: L.Polyline;
  private truckMarker?: L.Marker;
  private readonly fleetMarkers = new Map<number, L.Marker>();
  private routeSignature = '';
  private fleetIdsSignature = '';

  constructor() {
    afterNextRender(() => {
      this.initialize();
      this.render();
    });
    effect(() => {
      this.route();
      this.position();
      this.fleet();
      this.render();
    });
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private initialize(): void {
    if (this.map) {
      return;
    }
    this.map = L.map(this.mapElement().nativeElement, {
      zoomControl: true,
      attributionControl: true,
    }).setView([50.075538, 14.4378], 7);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(this.map);
  }

  private render(): void {
    if (!this.map) {
      return;
    }
    const points = this.route();
    const signature = points.map((point) => `${point.latitude},${point.longitude}`).join(';');
    if (signature !== this.routeSignature) {
      this.routeLayer?.remove();
      this.routeLayer = undefined;
      this.routeSignature = signature;
      if (points.length >= 2) {
        this.routeLayer = L.polyline(
          points.map((point) => [point.latitude, point.longitude] as L.LatLngTuple),
          { color: '#1565c0', weight: 5, opacity: 0.85 },
        ).addTo(this.map);
        this.map.fitBounds(this.routeLayer.getBounds(), { padding: [24, 24] });
      }
    }

    this.renderSinglePosition();
    this.renderFleet();
  }

  private renderSinglePosition(): void {
    if (!this.map) return;
    const position = this.position();
    if (!position) {
      this.truckMarker?.remove();
      this.truckMarker = undefined;
      return;
    }
    const latLng = L.latLng(position.latitude, position.longitude);
    if (!this.truckMarker) {
      this.truckMarker = L.marker(latLng, {
        zIndexOffset: 1000,
        icon: this.truckIcon(),
      }).addTo(this.map);
    } else {
      this.truckMarker.setLatLng(latLng);
    }
    this.rotateMarker(this.truckMarker, position.bearing);
  }

  private renderFleet(): void {
    if (!this.map) return;
    const fleet = this.fleet().filter(
      (item): item is FleetPosition & { position: TruckPosition } => item.position !== null,
    );
    const activeIds = new Set(fleet.map((item) => item.truckId));
    for (const [truckId, marker] of this.fleetMarkers) {
      if (!activeIds.has(truckId)) {
        marker.remove();
        this.fleetMarkers.delete(truckId);
      }
    }
    for (const item of fleet) {
      const latLng = L.latLng(item.position.latitude, item.position.longitude);
      let marker = this.fleetMarkers.get(item.truckId);
      if (!marker) {
        marker = L.marker(latLng, {
          zIndexOffset: 900,
          icon: this.truckIcon(),
        }).addTo(this.map);
        const tooltip = document.createElement('span');
        tooltip.textContent = `${item.plateNumber} · рейс #${item.shipmentId}`;
        marker.bindTooltip(tooltip, { direction: 'top', offset: [0, -16] });
        this.fleetMarkers.set(item.truckId, marker);
      } else {
        marker.setLatLng(latLng);
      }
      this.rotateMarker(marker, item.position.bearing);
    }
    const idsSignature = fleet.map((item) => item.truckId).sort((a, b) => a - b).join(',');
    if (fleet.length && idsSignature !== this.fleetIdsSignature) {
      this.fleetIdsSignature = idsSignature;
      this.map.fitBounds(
        L.latLngBounds(
          fleet.map(
            (item) => [item.position.latitude, item.position.longitude] as L.LatLngTuple,
          ),
        ),
        { padding: [30, 30], maxZoom: 11 },
      );
    }
  }

  private truckIcon(): L.DivIcon {
    return L.divIcon({
      className: 'cargo-truck-marker',
      html: '<span aria-hidden="true">CT</span>',
      iconSize: [38, 38],
      iconAnchor: [19, 19],
    });
  }

  private rotateMarker(marker: L.Marker, bearing: number): void {
    const glyph = marker.getElement()?.querySelector('span') as HTMLElement | null;
    if (glyph) {
      glyph.style.transform = `rotate(${bearing}deg)`;
    }
  }
}
