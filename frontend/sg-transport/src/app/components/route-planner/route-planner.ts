import { Component, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { BusStop, RouteLeg, RouteOption, RoutePlanResponse, TransportService } from '../../services/transport';

type SearchSide = 'from' | 'to';

@Component({
  selector: 'app-route-planner',
  imports: [FormsModule],
  templateUrl: './route-planner.html',
  styleUrl: './route-planner.css',
})
export class RoutePlanner implements OnDestroy {
  protected fromQuery = '';
  protected toQuery = '';
  protected readonly error = signal('');
  protected readonly fromResults = signal<BusStop[]>([]);
  protected readonly loadingSide = signal<SearchSide | null>(null);
  protected readonly planning = signal(false);
  protected readonly routePlan = signal<RoutePlanResponse | null>(null);
  protected readonly selectedFrom = signal<BusStop | null>(null);
  protected readonly selectedTo = signal<BusStop | null>(null);
  protected readonly toResults = signal<BusStop[]>([]);

  private routeRequest?: Subscription;
  private searchRequest?: Subscription;

  constructor(private readonly transportService: TransportService) {}

  ngOnDestroy(): void {
    this.routeRequest?.unsubscribe();
    this.searchRequest?.unsubscribe();
  }

  protected searchFrom(): void {
    this.search('from');
  }

  protected searchTo(): void {
    this.search('to');
  }

  protected selectFrom(busStop: BusStop): void {
    this.selectedFrom.set(busStop);
    this.fromQuery = this.stopLabel(busStop);
    this.fromResults.set([]);
    this.routePlan.set(null);
    this.error.set('');
  }

  protected selectTo(busStop: BusStop): void {
    this.selectedTo.set(busStop);
    this.toQuery = this.stopLabel(busStop);
    this.toResults.set([]);
    this.routePlan.set(null);
    this.error.set('');
  }

  protected planRoute(): void {
    const from = this.selectedFrom();
    const to = this.selectedTo();

    if (!from || !to) {
      this.error.set('Choose both bus stops.');
      this.routePlan.set(null);
      return;
    }

    if (from.code === to.code) {
      this.error.set('Choose two different bus stops.');
      this.routePlan.set(null);
      return;
    }

    this.error.set('');
    this.planning.set(true);
    this.routeRequest?.unsubscribe();
    this.routeRequest = this.transportService
      .planRoute(from.code, to.code)
      .pipe(finalize(() => this.planning.set(false)))
      .subscribe({
        next: (plan) => {
          this.routePlan.set(plan);

          if ((plan.routes ?? []).length === 0) {
            this.error.set('No direct or one-transfer bus routes found.');
          }
        },
        error: () => {
          this.routePlan.set(null);
          this.error.set('Unable to plan this route.');
        },
      });
  }

  protected canPlan(): boolean {
    return Boolean(this.selectedFrom() && this.selectedTo() && !this.planning());
  }

  protected optionTitle(option: RouteOption): string {
    return option.type === 'direct' ? 'Direct' : '1 transfer';
  }

  protected optionServices(option: RouteOption): string {
    return option.legs.map((leg) => leg.serviceNo).join(' + ');
  }

  protected legDirection(leg: RouteLeg): string {
    return `Direction ${leg.direction}`;
  }

  protected stopLabel(stop: BusStop): string {
    return `${stop.code} ${stop.name}`;
  }

  private search(side: SearchSide): void {
    const query = (side === 'from' ? this.fromQuery : this.toQuery).trim();

    if (!query) {
      this.error.set('Enter a bus stop name, road name, or code.');
      this.setResults(side, []);
      return;
    }

    this.error.set('');
    this.loadingSide.set(side);
    this.searchRequest?.unsubscribe();
    this.searchRequest = this.transportService
      .searchBusStops(query)
      .pipe(finalize(() => this.loadingSide.set(null)))
      .subscribe({
        next: (results) => {
          this.setResults(side, results);

          if (results.length === 0) {
            this.error.set('No matching bus stops found.');
          }
        },
        error: () => {
          this.setResults(side, []);
          this.error.set('Unable to reach the transport API.');
        },
      });
  }

  private setResults(side: SearchSide, results: BusStop[]): void {
    if (side === 'from') {
      this.fromResults.set(results);
    } else {
      this.toResults.set(results);
    }
  }
}
