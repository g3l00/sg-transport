declare module 'leaflet' {
  export type LatLngExpression = [number, number];

  export interface Map {
    invalidateSize(): this;
    remove(): this;
    setView(center: LatLngExpression, zoom: number): this;
  }

  export interface Layer {
    addTo(map: Map): this;
    remove(): this;
  }

  export interface CircleMarker extends Layer {
    bindPopup(content: string): this;
    openPopup(): this;
  }

  export interface TileLayer extends Layer {}

  export interface CircleMarkerOptions {
    color?: string;
    fillColor?: string;
    fillOpacity?: number;
    radius?: number;
    weight?: number;
  }

  export interface TileLayerOptions {
    attribution?: string;
    maxZoom?: number;
  }

  export function map(element: HTMLElement | string): Map;
  export function tileLayer(urlTemplate: string, options?: TileLayerOptions): TileLayer;
  export function circleMarker(
    latlng: LatLngExpression,
    options?: CircleMarkerOptions,
  ): CircleMarker;
}
