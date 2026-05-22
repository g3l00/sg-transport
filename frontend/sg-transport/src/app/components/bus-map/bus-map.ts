import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
  signal,
} from '@angular/core';
import type * as Leaflet from 'leaflet';
import { BusStop } from '../../services/transport';

type LeafletModule = typeof Leaflet & { default?: typeof Leaflet };

@Component({
  selector: 'app-bus-map',
  imports: [],
  templateUrl: './bus-map.html',
  styleUrl: './bus-map.css',
})
export class BusMap implements AfterViewInit, OnChanges, OnDestroy {
  @Input() busStop: BusStop | null = null;
  @ViewChild('mapContainer', { static: true })
  private mapContainer!: ElementRef<HTMLElement>;

  protected readonly mapError = signal('');

  private readonly singaporeCenter: [number, number] = [1.3521, 103.8198];
  private leaflet?: typeof Leaflet;
  private map?: Leaflet.Map;
  private stopMarker?: Leaflet.CircleMarker;

  async ngAfterViewInit(): Promise<void> {
    await this.initializeMap();
  }

  ngOnChanges(): void {
    this.renderSelectedStop();
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private async initializeMap(): Promise<void> {
    if (typeof window === 'undefined') {
      return;
    }

    try {
      const leafletModule = (await import('leaflet')) as LeafletModule;
      this.leaflet = leafletModule.default ?? leafletModule;
      this.map = this.leaflet
        .map(this.mapContainer.nativeElement)
        .setView(this.singaporeCenter, 12);

      this.leaflet
        .tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '&copy; OpenStreetMap contributors',
          maxZoom: 19,
        })
        .addTo(this.map);

      setTimeout(() => this.map?.invalidateSize(), 0);
      this.renderSelectedStop();
    } catch (error) {
      console.error('Leaflet map initialization failed', error);
      this.mapError.set('Map could not be loaded.');
    }
  }

  private renderSelectedStop(): void {
    if (!this.map || !this.leaflet) {
      return;
    }

    this.stopMarker?.remove();
    this.stopMarker = undefined;

    if (!this.hasCoordinates(this.busStop)) {
      this.map.setView(this.singaporeCenter, 12);
      return;
    }

    const coordinates: [number, number] = [this.busStop.latitude, this.busStop.longitude];
    this.stopMarker = this.leaflet
      .circleMarker(coordinates, {
        color: '#173b35',
        fillColor: '#2a9d8f',
        fillOpacity: 0.9,
        radius: 9,
        weight: 3,
      })
      .addTo(this.map);

    this.stopMarker
      .bindPopup(`<strong>${this.escapeHtml(this.busStop.name)}</strong><br>${this.busStop.code}`)
      .openPopup();

    this.map.setView(coordinates, 16);
  }

  private hasCoordinates(
    busStop: BusStop | null,
  ): busStop is BusStop & { latitude: number; longitude: number } {
    return (
      Boolean(busStop) &&
      typeof busStop?.latitude === 'number' &&
      typeof busStop?.longitude === 'number' &&
      Number.isFinite(busStop.latitude) &&
      Number.isFinite(busStop.longitude)
    );
  }

  private escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (character) => {
      const entities: Record<string, string> = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;',
      };

      return entities[character];
    });
  }
}
