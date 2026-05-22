import { Component, signal } from '@angular/core';
import { BusArrival } from './components/bus-arrival/bus-arrival';
import { BusMap } from './components/bus-map/bus-map';
import { BusSearch } from './components/bus-search/bus-search';
import { BusStop } from './services/transport';

@Component({
  selector: 'app-root',
  imports: [BusSearch, BusMap, BusArrival],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Bus Arrivals');
  protected readonly selectedStop = signal<BusStop | null>(null);

  protected selectBusStop(busStop: BusStop): void {
    this.selectedStop.set(busStop);
  }
}
