import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { BusArrival } from './bus-arrival';

describe('BusArrival', () => {
  let component: BusArrival;
  let fixture: ComponentFixture<BusArrival>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusArrival],
      providers: [provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(BusArrival);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
