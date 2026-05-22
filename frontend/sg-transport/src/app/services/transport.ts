import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

export interface BusStop {
  code: string;
  name: string;
  latitude: number | null;
  longitude: number | null;
  roadName: string;
}

export interface LtaBusStop {
  BusStopCode: string;
  RoadName: string;
  Description: string;
  Latitude: number;
  Longitude: number;
}

export interface BusStopsResponse {
  value: LtaBusStop[];
}

export interface BusArrivalEstimate {
  EstimatedArrival?: string;
  Load?: string;
  Feature?: string;
  Type?: string;
}

export interface BusServiceArrival {
  ServiceNo: string;
  Operator: string;
  NextBus?: BusArrivalEstimate;
  NextBus2?: BusArrivalEstimate;
  NextBus3?: BusArrivalEstimate;
}

export interface BusArrivalResponse {
  BusStopCode: string;
  Services: BusServiceArrival[];
}

@Injectable({
  providedIn: 'root',
})
export class TransportService {
  private readonly apiUrl = '/api/transport';

  constructor(private readonly http: HttpClient) {}

  searchBusStops(name: string): Observable<BusStop[]> {
    const query = name.trim();
    const params = new HttpParams().set('name', query);

    return this.http.get<BusStop[]>(`${this.apiUrl}/search`, { params }).pipe(
      switchMap((results) => {
        if (results.length > 0) {
          return of(results);
        }

        return this.searchFromBusStops(query);
      }),
      catchError(() => this.searchFromBusStops(query)),
    );
  }

  getBusArrival(busStopCode: string): Observable<BusArrivalResponse> {
    return this.http.get<BusArrivalResponse>(`${this.apiUrl}/bus-arrival/${busStopCode}`);
  }

  getBusStops(): Observable<BusStop[]> {
    return this.http.get<BusStopsResponse>(`${this.apiUrl}/bus-stops`).pipe(
      map((response) => response.value.map((stop) => this.toBusStop(stop))),
    );
  }

  private searchFromBusStops(query: string): Observable<BusStop[]> {
    const normalizedQuery = query.toLowerCase();

    return this.getBusStops().pipe(
      map((stops) =>
        stops
          .filter((stop) =>
            [stop.code, stop.name, stop.roadName]
              .join(' ')
              .toLowerCase()
              .includes(normalizedQuery),
          )
          .slice(0, 12),
      ),
    );
  }

  private toBusStop(stop: LtaBusStop): BusStop {
    return {
      code: stop.BusStopCode,
      name: stop.Description,
      latitude: stop.Latitude,
      longitude: stop.Longitude,
      roadName: stop.RoadName,
    };
  }
}
