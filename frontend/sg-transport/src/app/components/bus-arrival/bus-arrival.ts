import { Component, Input, OnDestroy, signal } from '@angular/core';
import { finalize, Subscription } from 'rxjs';
import {
  BusArrivalEstimate,
  BusServiceArrival,
  BusStop,
  TransportService,
} from '../../services/transport';

@Component({
  selector: 'app-bus-arrival',
  imports: [],
  templateUrl: './bus-arrival.html',
  styleUrl: './bus-arrival.css',
})
export class BusArrival implements OnDestroy {
  protected readonly arrivals = signal<BusServiceArrival[]>([]);
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly selectedStop = signal<BusStop | null>(null);

  private currentRequest?: Subscription;

  @Input() set busStop(value: BusStop | null) {
    this.selectedStop.set(value);
    this.loadArrivals();
  }

  constructor(private readonly transportService: TransportService) {}

  ngOnDestroy(): void {
    this.currentRequest?.unsubscribe();
  }

  protected refresh(): void {
    this.loadArrivals();
  }

  protected nextBuses(service: BusServiceArrival): BusArrivalEstimate[] {
    return [service.NextBus, service.NextBus2, service.NextBus3].filter(
      (bus): bus is BusArrivalEstimate => Boolean(bus?.EstimatedArrival),
    );
  }

  protected minutesUntil(bus: BusArrivalEstimate): string {
    if (!bus.EstimatedArrival) {
      return 'NA';
    }

    const arrivalTime = new Date(bus.EstimatedArrival).getTime();
    const minutes = Math.round((arrivalTime - Date.now()) / 60000);

    if (Number.isNaN(minutes)) {
      return 'NA';
    }

    return minutes <= 0 ? 'Arr' : `${minutes} min`;
  }

  protected arrivalClock(bus: BusArrivalEstimate): string {
    if (!bus.EstimatedArrival) {
      return '';
    }

    const arrivalDate = new Date(bus.EstimatedArrival);

    if (Number.isNaN(arrivalDate.getTime())) {
      return '';
    }

    return arrivalDate.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  protected loadClass(load?: string): string {
    const normalizedLoad = load?.toLowerCase() ?? '';

    if (normalizedLoad.includes('lsd') || normalizedLoad.includes('limited')) {
      return 'load-full';
    }

    if (normalizedLoad.includes('sda') || normalizedLoad.includes('standing')) {
      return 'load-medium';
    }

    if (normalizedLoad.includes('sea') || normalizedLoad.includes('seats')) {
      return 'load-open';
    }

    return 'load-neutral';
  }

  protected loadLabel(load?: string): string {
    switch (load) {
      case 'SEA':
        return 'Seats available';
      case 'SDA':
        return 'Standing available';
      case 'LSD':
        return 'Limited standing';
      default:
        return load || 'Load unavailable';
    }
  }

  private loadArrivals(): void {
    const busStop = this.selectedStop();

    this.currentRequest?.unsubscribe();
    this.arrivals.set([]);
    this.error.set('');

    if (!busStop) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.currentRequest = this.transportService
      .getBusArrival(busStop.code)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.arrivals.set(response.Services ?? []),
        error: () => this.error.set('Unable to load arrival times.'),
      });
  }
}
