import { Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { BusStop, TransportService } from '../../services/transport';

@Component({
  selector: 'app-bus-search',
  imports: [FormsModule],
  templateUrl: './bus-search.html',
  styleUrl: './bus-search.css',
})
export class BusSearch {
  @Output() stopSelected = new EventEmitter<BusStop>();

  protected query = 'Victoria';
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly results = signal<BusStop[]>([]);
  protected readonly selectedCode = signal<string | null>(null);

  constructor(private readonly transportService: TransportService) {}

  protected search(): void {
    const query = this.query.trim();

    if (!query) {
      this.error.set('Enter a bus stop name, road name, or code.');
      this.results.set([]);
      return;
    }

    this.error.set('');
    this.loading.set(true);

    this.transportService
      .searchBusStops(query)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (results) => {
          this.results.set(results);

          if (results.length === 0) {
            this.error.set('No matching bus stops found.');
          }
        },
        error: () => {
          this.results.set([]);
          this.error.set('Unable to reach the transport API.');
        },
      });
  }

  protected selectStop(busStop: BusStop): void {
    this.selectedCode.set(busStop.code);
    this.stopSelected.emit(busStop);
  }

  protected canCheckByCode(): boolean {
    return /^\d{5}$/.test(this.query.trim());
  }

  protected checkByCode(): void {
    const code = this.query.trim();

    if (!this.canCheckByCode()) {
      return;
    }

    this.selectStop({
      code,
      name: `Bus stop ${code}`,
      latitude: null,
      longitude: null,
      roadName: 'Manual lookup',
    });
  }
}
