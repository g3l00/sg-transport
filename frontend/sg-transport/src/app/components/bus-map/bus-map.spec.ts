import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BusMap } from './bus-map';

describe('BusMap', () => {
  let component: BusMap;
  let fixture: ComponentFixture<BusMap>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusMap],
    }).compileComponents();

    fixture = TestBed.createComponent(BusMap);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
